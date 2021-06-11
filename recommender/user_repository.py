import json
from collections import defaultdict


def get_user_repository(user_db_json_file="data/user_db.json"):
    repo = UserRepository()
    with open(user_db_json_file, "r") as f:
        data = json.load(f)
        repo.add_all_user_favourites(data)
    return repo


class UserRepository:
    def __init__(self):
        self.repository = defaultdict(set)

    def add_all_user_favourites(self, user_favourites_map):
        for user, favs in user_favourites_map.items():
            self.add_user_favourites(user, favs)

    def add_user_favourites(self, user, favs):
        self.repository[user].update(favs)

    def get_user_favourites(self, user):
        return self.repository[user]

    def save_db(self, data_file="data/user_db.json"):
        dict = {k: list(v) for (k, v) in self.repository.items()}
        with open(data_file, "w") as f:
            json.dump(dict, f, indent=2)
