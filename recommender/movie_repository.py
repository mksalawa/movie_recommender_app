import json

import pandas as pd


class Context:
    def __init__(self, time, location, companion, user_id=None):
        self.time = time
        self.location = location
        self.companion = companion
        self.user_id = user_id

    def to_json(self):
        return json.dumps(self.__dict__, indent=2)

    @classmethod
    def from_json(cls, json_str):
        json_dict = json.loads(json_str)
        return cls(**json_dict)


class MovieMetadata:
    def __init__(self, movie_id, title, director, genres, actor_1, actor_2, actor_3,
                 plot_keywords, language, country, imdb_score):
        self.movie_id = movie_id
        self.title = title
        self.director = director
        self.genres = genres
        self.actor_1 = actor_1
        self.actor_2 = actor_2
        self.actor_3 = actor_3
        self.plot_keywords = plot_keywords
        self.language = language
        self.country = country
        self.imdb_score = imdb_score

    def to_json(self):
        return json.dumps(self.__dict__, indent=2)

    @classmethod
    def from_json(cls, json_str):
        json_dict = json.loads(json_str)
        return cls(**json_dict)


def get_movie_repository(metadata_file="data/depaulmovie/filtered_metadata.csv"):
    metadata = pd.read_csv(metadata_file, sep=',')
    return MovieRepository(metadata)


def load_rating_data(ratings_file="data/depaulmovie/ratings.txt"):
    return pd.read_csv(ratings_file, sep=',')


class MovieRepository:
    def __init__(self, metadata_df):
        self.repository = metadata_df

    @staticmethod
    def _create_movie_obj_from_df(metadata):
        metadata_dict = metadata.to_dict()
        return MovieMetadata(**metadata_dict)

    @staticmethod
    def transform_df_to_objs(metadata_df):
        return [MovieRepository._create_movie_obj_from_df(row) for _, row in metadata_df.iterrows()]

    def get_by_title(self, title):
        m = self.repository.loc[self.repository['title'] == title]
        if m.shape[0] > 0:
            return MovieRepository.transform_df_to_objs(m)[0]
        return None

    def get_by_ids(self, movie_ids):
        if len(movie_ids) == 0:
            return []
        metadata = self._get_metadata_by_movie_ids(movie_ids)
        return MovieRepository.transform_df_to_objs(metadata)

    def get_raw_metadata(self):
        return self.repository.copy()

    def titles_to_ids(self, titles):
        return self.repository.loc[self.repository['title'].isin(titles)]['movie_id']

    def _get_metadata_by_movie_ids(self, movie_ids):
        return self.repository.set_index('movie_id').loc[movie_ids].reset_index()

