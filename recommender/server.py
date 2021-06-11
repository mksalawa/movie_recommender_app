import json
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from urlparse import urlparse, parse_qs

from movie_recommenders import KnnItemCollaborativeRecommender, ContentBasedRecommender
from movie_repository import get_movie_repository, load_rating_data, Context
from user_repository import get_user_repository

hostName = "192.168.0.129"
hostPort = 7777


def obj_list_to_json(obj_list):
    return json.dumps(obj_list, default=(lambda x: x.__dict__), indent=2)


class RecommendationHandler(BaseHTTPRequestHandler):

    def _send_headers(self, code):
        self.send_response(code)
        self.send_header('Content-type', 'application/json')
        self.end_headers()

    def do_GET(self):
        parsed_url = urlparse(self.path)
        if parsed_url.path == "/":
            # Parse query params
            # e.g. /?u=username&t=Weekday&loc=Cinema&comp=Alone
            query_components = parse_qs(parsed_url.query)
            username = query_components['u'][0]
            favs = self.user_repository.get_user_favourites(username)
            if not favs:
                # user with no favourites, return error msg
                self._send_headers(400)
                error_msg = {"error": "No favourite movies found."}
                self.wfile.write(json.dumps(error_msg))
                return

            # TODO: handle missing parameters.
            ctx = Context(query_components['t'][0], query_components['loc'][0], query_components['comp'][0])
            recommendations = {}
            for key, rec in self.recommenders.items():
                movie_data = rec.get_full_predictions(favs, ctx, 10)
                recommendations[key] = movie_data

            recommendations_json = obj_list_to_json(recommendations)
            self._send_headers(200)
            self.wfile.write(recommendations_json)
            return

        elif parsed_url.path == "/favourites":
            query_components = parse_qs(parsed_url.query)
            username = query_components['u'][0]
            favs = self.user_repository.get_user_favourites(username)
            movie_data = self.movie_repository.get_by_ids(favs)

            recommendations_json = obj_list_to_json(movie_data)
            self._send_headers(200)
            self.wfile.write(recommendations_json)
            return

    def do_POST(self):
        parsed_url = urlparse(self.path)
        if parsed_url.path == "/favourites":
            query_components = parse_qs(parsed_url.query)
            username = query_components['u'][0]

            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            titles = json.loads(post_data, encoding='utf-8')

            found_data = []
            failed = []
            for title in titles:
                movie = self.movie_repository.get_by_title(title)
                if movie is not None:
                    # add to db
                    self.user_repository.add_user_favourites(username, [movie.movie_id])
                    found_data.append(movie)
                else:
                    failed.append(title)

            if len(found_data) > 0:
                favs_json = obj_list_to_json(found_data)
                self._send_headers(200)
                self.wfile.write(favs_json)
            else:
                self._send_headers(400)
                error_msg = {"error": "Movies not found: {0}.".format(", ".join(failed))}
                self.wfile.write(json.dumps(error_msg))


if __name__ == '__main__':

    rating_data = load_rating_data()
    user_db = get_user_repository()
    movie_db = get_movie_repository()

    collab_filtering = KnnItemCollaborativeRecommender(rating_data, movie_db)
    content_based = ContentBasedRecommender(rating_data, movie_db)
    RecommendationHandler.recommenders = {
        "collab": collab_filtering,
        "content": content_based,
    }
    RecommendationHandler.user_repository = user_db
    RecommendationHandler.movie_repository = movie_db

    recServer = HTTPServer((hostName, hostPort), RecommendationHandler)
    print(time.asctime(), "Server starts on: %s:%s" % (hostName, hostPort))

    try:
        recServer.serve_forever()
    except KeyboardInterrupt:
        user_db.save_db()
        recServer.server_close()
        print(time.asctime(), "Server stops on: %s:%s" % (hostName, hostPort))
