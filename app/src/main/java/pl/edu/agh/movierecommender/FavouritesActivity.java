package pl.edu.agh.movierecommender;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FavouritesActivity extends AppCompatActivity {

    private static final String SERVER_ENDPOINT = MainActivity.SERVER_ADDRESS + "/favourites";
    private static final String KEY_FAVOURITES_JSON = "KEY_FAVOURITES_JSON";
    private static final String KEY_ADDED_FAVOURITES_JSON = "KEY_ADDED_FAVOURITES_JSON";
    private static final String KEY_ADDED_FAVOURITES_ERROR = "KEY_ADDED_FAVOURITES_ERROR";
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    private EditText inputFavourite;
    private Button buttonAddFavourite;
    private ListView favouritesListView;
    private String username;

    private Handler favouritesHandler;
    private Handler addFavouritesHandler;

    private MovieListAdapter favouritesAdapter;
    private List<MovieCard> favouritesCards = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favourites);

        getSupportActionBar().setTitle("Your Favourites");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        SharedPreferences prefs = this.getSharedPreferences(MainActivity.MOVIE_RECOMMENDER_PREFS_KEY, MODE_PRIVATE);
        username = prefs.getString(MainActivity.KEY_USERNAME, "");
        TextView textViewUsername = findViewById(R.id.textViewFavouritesUsernameValue);
        textViewUsername.setText(username);

        inputFavourite = findViewById(R.id.inputFavourite);
        buttonAddFavourite = findViewById(R.id.buttonAddFavourite);
        favouritesListView = findViewById(R.id.listViewForFavourites);

        favouritesHandler = new FavouritesHandler(this);
        addFavouritesHandler = new AddFavouritesHandler(this);

        buttonAddFavourite.setOnClickListener(v -> {
            String input = inputFavourite.getText().toString();
            if (input.length() == 0) {
                Toast.makeText(getApplicationContext(), "Please provide the movie title.", Toast.LENGTH_SHORT).show();
                return;
            }
            inputFavourite.getText().clear();
            new Thread(() -> addToFavourites(input)).start();
        });

        favouritesAdapter = new MovieListAdapter(this, R.layout.card_view_layout, favouritesCards);
        favouritesListView.setAdapter(favouritesAdapter);

        Toast.makeText(getApplicationContext(), "Fetching favourites...", Toast.LENGTH_SHORT).show();
        new Thread(() -> fetchFavourites(username)).start();
    }

    private void fetchFavourites(String username) {
        String url = HttpUrl.parse(SERVER_ENDPOINT).newBuilder()
                .addQueryParameter("u", encodeValue(username))
                .build().toString();

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = client.newCall(request).execute()) {
            Message msg = favouritesHandler.obtainMessage();
            Bundle data = new Bundle();
            if (response.isSuccessful()) {
                data.putString(KEY_FAVOURITES_JSON, response.body().string());
            }
            msg.setData(data);
            favouritesHandler.sendMessage(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String encodeValue(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return "";
    }

    private static class FavouritesHandler extends Handler {

        private final WeakReference<FavouritesActivity> mActivity;

        FavouritesHandler(FavouritesActivity activity) {
            this.mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            FavouritesActivity activity = mActivity.get();

            Bundle data = msg.getData();
            String response = data.getString(KEY_FAVOURITES_JSON);
            List<MovieMetadata> metadata = new Gson().fromJson(
                    response, new TypeToken<List<MovieMetadata>>(){}.getType());

            if (metadata == null || metadata.isEmpty()) {
                Toast.makeText(activity.getApplicationContext(),
                        "No favourite movies found.", Toast.LENGTH_LONG).show();
                return;
            }

            ArrayList<MovieCard> movieCards = new ArrayList<>();
            metadata.forEach(m -> {
                movieCards.add(new MovieCard(m, MainActivity.getImageUrl(m.getMovieId(), activity)));
            });

            activity.favouritesAdapter.addAll(movieCards);
            activity.favouritesAdapter.notifyDataSetChanged();
        }
    }

    private void addToFavourites(String title) {
        String url = HttpUrl.parse(SERVER_ENDPOINT).newBuilder()
                .addQueryParameter("u", encodeValue(username))
                .build().toString();

        // Body needs to be a JSON list of titles
        String[] titles = {title};
        RequestBody body = RequestBody.create(new Gson().toJson(titles), JSON_TYPE);

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            Message msg = addFavouritesHandler.obtainMessage();
            Bundle data = new Bundle();
            if (response.isSuccessful()) {
                data.putString(KEY_ADDED_FAVOURITES_JSON, response.body().string());
            } else if (response.code() == 400) {
                JSONObject res = new JSONObject(response.body().string());
                if (res.has("error") && (res.getString("error").startsWith("Movies not found"))) {
                    data.putString(KEY_ADDED_FAVOURITES_ERROR, res.getString("error"));
                }
            }
            msg.setData(data);
            addFavouritesHandler.sendMessage(msg);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private static class AddFavouritesHandler extends Handler {

        private final WeakReference<FavouritesActivity> mActivity;

        AddFavouritesHandler(FavouritesActivity activity) {
            this.mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            FavouritesActivity activity = mActivity.get();

            Bundle data = msg.getData();
            String errorMsg = data.getString(KEY_ADDED_FAVOURITES_ERROR);
            if (errorMsg != null) {
                // Show toast with the error
                Toast.makeText(activity.getApplicationContext(), errorMsg, Toast.LENGTH_LONG).show();
                return;
            }

            String response = data.getString(KEY_ADDED_FAVOURITES_JSON);
            List<MovieMetadata> metadata = new Gson().fromJson(
                    response, new TypeToken<List<MovieMetadata>>(){}.getType());

            List<MovieCard> addedMovies = new ArrayList<>();
            metadata.forEach(e -> {
                addedMovies.add(new MovieCard(e, MainActivity.getImageUrl(e.getMovieId(), activity)));
            });

            List<Long> existingFavs = activity.favouritesCards.stream()
                    .map(e -> e.getMetadata().getMovieId())
                    .collect(Collectors.toList());
            for (MovieCard addedMovie : addedMovies) {
                if (!existingFavs.contains(addedMovie.getMetadata().getMovieId())) {
                    activity.favouritesAdapter.add(addedMovie);
                }
            }
            activity.favouritesAdapter.notifyDataSetChanged();
        }
    }
}
