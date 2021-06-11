package pl.edu.agh.movierecommender;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    protected static final String IMDB_BASE_URL = "https://www.imdb.com/title/";
    protected static final String SERVER_ADDRESS = "http://192.168.0.129:7777";
    protected static final String KEY_USERNAME = "KEY_USERNAME";
    protected static final String MOVIE_RECOMMENDER_PREFS_KEY = "MOVIE_RECOMMENDER_PREFS_KEY";
    protected static final int FAVS_REQUEST_CODE = 159;

    private static final String KEY_RECOMMENDATIONS_JSON = "KEY_RECOMMENDATIONS_JSON";
    private static final String KEY_RECOMMENDATIONS_ERROR = "KEY_RECOMMENDATIONS_ERROR";
    private static final int MY_PERMISSIONS_REQUEST_LOCATION = 147;

    private TextView textViewDateTime;
    private TextView textViewLocation;
    private RadioButton buttonHome;
    private RadioButton buttonCinema;
    private RadioGroup radioGroupLocation;
    private RadioButton buttonAlone;
    private RadioButton buttonPartner;
    private RadioButton buttonFamily;
    private RadioGroup radioGroupCompanion;
    private Button buttonRecommend;
    private ImageButton buttonSettings;
    private ListView collabMovieListView ;
    private ListView contentMovieListView ;

    private final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("HH:mm, EEE, MMM d, yyyy");
    private Calendar calendar = Calendar.getInstance();
    private String username;

    private Handler recommendationHandler;
    private LocationManager locationManager;
    private LocationProvider locationProvider;
    private LocationListener locationListener;

    private TabLayout tabLayout;
    private SectionsPagerAdapter mSectionsPagerAdapter;
    private ViewPager mViewPager;


    private SharedPreferences sharedPrefs;

    @Override
    protected void onStart() {
        super.onStart();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_LOCATION);
            // MY_PERMISSIONS_REQUEST_LOCATION is an app-defined int constant.
            // The callback method gets the result of the request.
        } else{
            accessLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] ==
                        PackageManager.PERMISSION_GRANTED) {
                    accessLocation();
                }
            }
        }
    }

    private void accessLocation(){
        this.locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        this.locationProvider = this.locationManager.getProvider(LocationManager.GPS_PROVIDER);
        if (locationProvider != null) {
//            Toast.makeText(this, "Location listener registered!", Toast.LENGTH_SHORT).show();
            try {
                this.locationManager.requestLocationUpdates(locationProvider.getName(), 0, 0,
                        this.locationListener);
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        } else {
//            Toast.makeText(this,
//                    "Location Provider is not avilable at the moment!",
//                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textViewDateTime = findViewById(R.id.textViewTimeDateValue);
        textViewLocation = findViewById(R.id.textViewLocationValue);

        buttonHome = findViewById(R.id.radioButtonLocationHome);
        buttonCinema = findViewById(R.id.radioButtonLocationCinema);
        radioGroupLocation = findViewById(R.id.radioGroupLocation);

        buttonAlone = findViewById(R.id.radioButtonCompanionAlone);
        buttonPartner = findViewById(R.id.radioButtonCompanionPartner);
        buttonFamily = findViewById(R.id.radioButtonCompanionFamily);
        radioGroupCompanion = findViewById(R.id.radioGroupCompanion);

        final String timeOfWeek = getTimeOfWeek(calendar.get(Calendar.DAY_OF_WEEK));
        String strDate = dateTimeFormat.format(calendar.getTime()) + " (" + timeOfWeek + ")";
        textViewDateTime.setText(strDate);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // Use SharedPreferences for the username instead of the extra strings in Intents
        sharedPrefs = this.getSharedPreferences(MOVIE_RECOMMENDER_PREFS_KEY, MODE_PRIVATE);
        username = sharedPrefs.getString(KEY_USERNAME, "");

        buttonSettings = findViewById(R.id.buttonSettings);
        buttonRecommend = findViewById(R.id.buttonRecommend);

        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());
        mViewPager = findViewById(R.id.viewPager);
        setupViewPager(mViewPager);
        tabLayout = findViewById(R.id.tabLayout);
        tabLayout.setupWithViewPager(mViewPager);

        collabMovieListView = findViewById(R.id.listViewForCollab);
        contentMovieListView = findViewById(R.id.listViewForContent);

        recommendationHandler = new RecommendationHandler(this);

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                final double lat = (location.getLatitude());
                final double lon = location.getLongitude();
                textViewLocation.setText(String.format("(%6f, %6f)", lat, lon));
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}

            @Override
            public void onProviderEnabled(String provider) {}

            @Override
            public void onProviderDisabled(String provider) {}
        };

        buttonSettings.setOnClickListener(v -> {
            // Open Favourites Activity
            Intent intent = new Intent(getBaseContext(), FavouritesActivity.class);
            startActivity(intent);
        });

        buttonRecommend.setOnClickListener(v -> {
            // Collect the query data
            // TimeOfWeek, Location, Companion
            final String location;
            final String companion;

            int selected = radioGroupLocation.getCheckedRadioButtonId();
            if(selected == buttonHome.getId()) {
                location = "Home";
            } else if(selected == buttonCinema.getId()) {
                location = "Cinema";
            } else {
                Toast.makeText(getApplicationContext(), "Please fill the context information.", Toast.LENGTH_SHORT).show();
                return;
            }
            selected = radioGroupCompanion.getCheckedRadioButtonId();
            if(selected == buttonAlone.getId()) {
                companion = "Alone";
            } else if(selected == buttonPartner.getId()) {
                companion = "Partner";
            } else if(selected == buttonFamily.getId()) {
                companion = "Family";
            } else {
                Toast.makeText(getApplicationContext(), "Please fill the context information.", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(getApplicationContext(), "Searching for recommendations...", Toast.LENGTH_SHORT).show();
            new Thread(() -> requestRecommendations(timeOfWeek, location, companion)).start();
        });

    }

    private void setupViewPager(ViewPager viewPager) {
        SectionsPagerAdapter adapter = new SectionsPagerAdapter(getSupportFragmentManager());
        adapter.addFragment(new CollabFragment(), "Others also like");
        adapter.addFragment(new ContentFragment(), "Similar to your favourites");
        viewPager.setAdapter(adapter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (locationProvider != null) {
            // Toast.makeText(this, "Location listener unregistered!", Toast.LENGTH_SHORT).show();
            try {
                this.locationManager.removeUpdates(this.locationListener);
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        } else {
            // Toast.makeText(this, "Location Provider is not avilable at the moment!",
            //                Toast.LENGTH_SHORT).show();
        }
    }

    private void requestRecommendations(String timeOfWeek, String location, String companion) {
        // Should already be set up.
        // username = sharedPrefs.getString(KEY_USERNAME, "default");
        String url = HttpUrl.parse(SERVER_ADDRESS).newBuilder()
                .addQueryParameter("u", encodeValue(username))
                .addQueryParameter("t", encodeValue(timeOfWeek))
                .addQueryParameter("loc", encodeValue(location))
                .addQueryParameter("comp", encodeValue(companion))
                .build().toString();

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = client.newCall(request).execute()) {
            Message msg = recommendationHandler.obtainMessage();
            Bundle data = new Bundle();
            // Response contains recommendations or error.
            if (response.isSuccessful()) {
                data.putString(KEY_RECOMMENDATIONS_JSON, response.body().string());
            } else if (response.code() == 400) {
                JSONObject res = new JSONObject(response.body().string());
                if (res.has("error") && (res.getString("error").startsWith("No favourite"))) {
                    data.putString(KEY_RECOMMENDATIONS_ERROR,
                            "Please add your favourite movies in the profile settings.");
                }
            }
            msg.setData(data);
            recommendationHandler.sendMessage(msg);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
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

    private static class RecommendationHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;

        RecommendationHandler(MainActivity activity) {
            this.mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity activity = mActivity.get();

            Bundle data = msg.getData();
            String errorMsg = data.getString(KEY_RECOMMENDATIONS_ERROR);
            if (errorMsg != null) {
                // Show toast with the error
                Toast.makeText(activity.getApplicationContext(), errorMsg, Toast.LENGTH_LONG).show();
                return;
            }

            String response = data.getString(KEY_RECOMMENDATIONS_JSON);
            Map<String, List<MovieMetadata>> metadataMap = new Gson().fromJson(
                    response, new TypeToken<Map<String, List<MovieMetadata>>>() {}.getType());

            for (String key : metadataMap.keySet()) {
                switch (key) {
                    case "collab":
                        ArrayList<MovieCard> movieCards = new ArrayList<>();
                        metadataMap.get(key).forEach(e -> {
                            // String movieId = String.format("tt%07d", e.getMovieId());
                            movieCards.add(new MovieCard(e, getImageUrl(e.getMovieId(), activity)));
                        });

                        MovieListAdapter adapter = new MovieListAdapter(activity, R.layout.card_view_layout, movieCards);
                        activity.collabMovieListView = activity.findViewById(R.id.listViewForCollab);
                        activity.collabMovieListView.setAdapter(adapter);
                        break;
                    case "content":
                        ArrayList<MovieCard> contentBasedCards = new ArrayList<>();
                        metadataMap.get(key).forEach(e -> {
                            // String movieId = String.format("tt%07d", e.getMovieId());
                            contentBasedCards.add(new MovieCard(e, getImageUrl(e.getMovieId(), activity)));
                        });

                        MovieListAdapter contentBasedAdapter = new MovieListAdapter(activity, R.layout.card_view_layout, contentBasedCards);
                        activity.contentMovieListView = activity.findViewById(R.id.listViewForContent);
                        activity.contentMovieListView.setAdapter(contentBasedAdapter);
                        break;
                }
            }
        }
    }

    private String getTimeOfWeek(int day) {
        switch (day) {
            case Calendar.SUNDAY:
            case Calendar.SATURDAY:
                return "Weekend";
            default:
                return "Weekday";
        }
    }

    protected static String getImageUrl(long movieId, Context context) {
//        Resources resources = context.getResources();
//        final int resourceId = resources.getIdentifier(String.format("tt%07d.jpg", movieId), "drawable",
//                context.getPackageName());
//        if (resourceId == 0) {
//            return "drawable://" + R.drawable.image_failed;
//        }
        return "drawable://" + R.drawable.tt0133093;
    }
}
