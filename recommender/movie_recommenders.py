import pandas as pd
import numpy as np
from scipy.sparse import csr_matrix
from sklearn.feature_extraction.text import CountVectorizer
from sklearn.metrics.pairwise import cosine_similarity

from sklearn.neighbors import NearestNeighbors


class KnnItemCollaborativeRecommender:
    def __init__(self, ratings_df, movie_repository):
        self.ratings_df = ratings_df
        self.movie_repository = movie_repository
        self.model = NearestNeighbors(metric='cosine', algorithm='brute')

    def get_full_predictions(self, fav_movie_ids, ctx, n_recommendations):
        dists, recommends = self.make_recommendations(fav_movie_ids, ctx, n_recommendations)
        return recommends

    def make_recommendations(self, fav_movie_ids, ctx, n_recommendations):
        """
        Make top n movie recommendations using Item-Based Collaborative Filtering.

        :param fav_movie_ids: list(int), list of favourite movie ids
        :param ctx: Context, user context for recommendations
        :param n_recommendations: int, top n recommendations
        """
        # pre-filtering of context data
        ctx_filter = (self.ratings_df.time.str.contains(ctx.time)) | (
            self.ratings_df.location.str.contains(ctx.location)) | (
            self.ratings_df.companion.str.contains(ctx.companion))

        filtered_data = self.ratings_df[ctx_filter]
        # we have the filtered data - now we can use a common classifier, discarding the context value
        # (it is the same for all datapoints now)
        final_data = pd.pivot_table(filtered_data, index=['movie_id'], columns=['user_id'], values=['rating']).fillna(0)

        sparse_data = csr_matrix(final_data.values)
        self.model.fit(sparse_data)

        # 1. for each of the movie_ids find the neighbours
        #    (kneighbors() can take multiple points and will search for each)
        # 2. merge the lists by distance, ignoring the movies already in favourites
        # 3. until the best n_recommendations found

        fav_sparse_indices = [final_data.index.get_loc(movie_id)
                              for movie_id in fav_movie_ids
                              if movie_id in final_data.index]
        distances, indices = self.model.kneighbors(sparse_data[fav_sparse_indices], n_neighbors=n_recommendations + 1)

        # shape: (len(fav_sparse_indices), n_recommendations + 1)
        # argsort distances in 2d and choose these indices
        sorted_2d_indices = np.dstack(np.unravel_index(
            np.argsort(distances.ravel()), (len(fav_sparse_indices), n_recommendations + 1)
        ))[0]

        final_movie_ids = []
        final_movie_dists = []
        found = 0
        for row in sorted_2d_indices:
            sparse_movie_id = indices[row[0], row[1]]
            if sparse_movie_id not in fav_sparse_indices and final_data.index[sparse_movie_id] not in final_movie_ids:
                # recommend only different than already favourite
                final_movie_ids.append(final_data.index[sparse_movie_id])
                final_movie_dists.append(distances[row[0], row[1]])
                found += 1
            if found >= n_recommendations:
                break
        final_movie_ids = np.array(final_movie_ids)

        final_metadata = self.movie_repository.get_by_ids(final_movie_ids)
        for i, (dist, metadata) in enumerate(zip(final_movie_dists, final_metadata)):
            print('{0}: (distance = {1})\t{2} - {3}'.format(i + 1, dist, metadata.movie_id, metadata.title))

        return final_movie_dists, final_metadata


class ContentBasedRecommender:
    def __init__(self, ratings_df, movie_repository):
        self.ratings_df = ratings_df
        self.movie_repository = movie_repository
        self.metadata = None
        self.movie_index = None
        self.similarity_matrix = None
        self.preprocessed = False

    def preprocess_data(self):
        # Make a copy of the raw data that will be then transformed
        self.metadata = self.movie_repository.get_raw_metadata()

        # clean up the data
        # 1. Replace NaNs with empty values
        self.metadata.fillna('', inplace=True)
        # 2. Lowercase all strings, split lists, join first and last names
        self.metadata['genres'] = self.metadata['genres'].map(lambda x: x.lower().split('|'))
        self.metadata['language'] = self.metadata['language'].map(lambda x: x.lower().split('|'))
        self.metadata['plot_keywords'] = self.metadata['plot_keywords'].map(
            lambda x: x.lower().replace(' ', '').split('|'))
        self.metadata['director'] = self.metadata['director'].map(lambda x: x.lower().replace(' ', ''))
        self.metadata['actor_1'] = self.metadata['actor_1'].map(lambda x: x.lower().replace(' ', ''))
        self.metadata['actor_2'] = self.metadata['actor_2'].map(lambda x: x.lower().replace(' ', ''))
        self.metadata['actor_3'] = self.metadata['actor_3'].map(lambda x: x.lower().replace(' ', ''))
        self.metadata['country'] = self.metadata['country'].map(lambda x: x.lower())

        self.metadata.set_index('movie_id', inplace=True)
        self.movie_index = pd.Series(self.metadata.index)

        self.metadata['bag_of_words'] = ''
        for index, row in self.metadata.iterrows():
            words = ''
            for col in ['director', 'actor_1', 'actor_2', 'actor_3', 'country']:
                words = words + row[col] + ' '
            for col in ['genres', 'language', 'plot_keywords']:
                words = words + ' '.join(row[col]) + ' '

            self.metadata.loc[index, 'bag_of_words'] = words

        self.metadata.drop(columns=[col for col in self.metadata.columns
                                    if col not in ['bag_of_words', 'movie_id']], inplace=True)

        self.similarity_matrix = self._compute_similarity_matrix()
        self.preprocessed = True

    def _compute_similarity_matrix(self):
        # The word count matrix
        count = CountVectorizer()
        count_matrix = count.fit_transform(self.metadata['bag_of_words'])
        # The cosine similarity matrix
        return cosine_similarity(count_matrix, count_matrix)

    def get_full_predictions(self, fav_movie_ids, ctx, n_recommendations):
        """
        Make top n movie recommendations based on the item characteristics (Content-Based Recommendations).

        :param fav_movie_ids: list(int), list of favourite movie ids
        :param ctx: Context, user context for recommendations
        :param n_recommendations: int, top n recommendations
        """
        if not self.preprocessed:
            self.preprocess_data()

        # pre-filtering of context data
        ctx_filter = (self.ratings_df.time.str.contains(ctx.time)) | (
            self.ratings_df.location.str.contains(ctx.location)) | (
            self.ratings_df.companion.str.contains(ctx.companion))

        movies_to_watch_in_ctx = self.ratings_df[ctx_filter]['movie_id'].unique()
        ctx_movies_ids = self.movie_index[self.movie_index.isin(movies_to_watch_in_ctx)].index

        tops_found = {}  # id in similarity matrix : distance
        fav_ids = self.movie_index[self.movie_index.isin(fav_movie_ids)].index
        for fav_id in fav_ids:
            # Find n_recommendations for the movie
            # # idx = self.movie_index[self.movie_index == fav_movie_id].index[0]
            # Find n+1 largest (similarity is maximum for the movie itself)
            score_series = pd.Series(self.similarity_matrix[fav_id]).sort_values(ascending=False)
            # Filter only the movies that were rated in the current context
            top_idxs = score_series.index[score_series.index.isin(ctx_movies_ids)][1:n_recommendations+1]

            for idx in top_idxs:
                found_similarity = score_series[idx]
                if idx in fav_ids:
                    # ignore already favourites
                    continue

                if idx in tops_found:
                    # if movie was already in recommended, overwrite if a larger similarity found
                    if found_similarity > tops_found[idx]:
                        tops_found[idx] = found_similarity
                else:
                    tops_found[idx] = found_similarity

        final_recommended_ids = sorted(tops_found, key=tops_found.get, reverse=True)[:n_recommendations]
        final_movie_ids = self.movie_index[final_recommended_ids]
        final_metadata = self.movie_repository.get_by_ids(final_movie_ids)
        for i, metadata in enumerate(final_metadata):
            print('{0}: (similarity = {1})\t{2} - {3}'.format(
                i + 1, tops_found[final_recommended_ids[i]], metadata.movie_id, metadata.title))
        return final_metadata
