package pl.edu.agh.movierecommender;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.nostra13.universalimageloader.cache.memory.impl.WeakMemoryCache;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;
import com.nostra13.universalimageloader.core.display.FadeInBitmapDisplayer;

import java.util.List;

class MovieListAdapter extends ArrayAdapter<MovieCard> {

    private Context mContext;
    private int mResource;
    private int lastPosition = -1;

    private List<MovieCard> cards;

    /**
     * Holds variables in a View
     */
    private static class ViewHolder {
        TextView title;
        TextView director;
        ImageView image;
    }

    /**
     * Default constructor for the MovieListAdapter
     * @param context
     * @param resource
     * @param objects
     */
    public MovieListAdapter(Context context, int resource, List<MovieCard> objects) {
        super(context, resource, objects);
        mContext = context;
        mResource = resource;
        cards = objects;
    }

    @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        //sets up the image loader library
        setupImageLoader();

        //get the movie information
        // TODO: Fill the remaining data
        String title = getItem(position).getMetadata().getTitle();
        String director = getItem(position).getMetadata().getDirector();
        String imgUrl = getItem(position).getImgUrl();

        //create the view result for showing the animation
        final View result;

        //ViewHolder object
        ViewHolder holder;

//        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            convertView = inflater.inflate(mResource, parent, false);
            holder= new ViewHolder();
            holder.title = convertView.findViewById(R.id.textViewCardTitle);
            holder.director = convertView.findViewById(R.id.textViewCardDirector);
            // holder.url = convertView.findViewById(R.id.te);
            holder.image = convertView.findViewById(R.id.imageViewCard);

            result = convertView;

            convertView.setTag(holder);
//        } else {
//            holder = (ViewHolder) convertView.getTag();
//            result = convertView;
//        }


        Animation animation = AnimationUtils.loadAnimation(mContext,
                (position > lastPosition) ? R.anim.load_down_anim : R.anim.load_up_anim);
        result.startAnimation(animation);
        lastPosition = position;

        holder.title.setText(title);
        holder.director.setText(director);
//        holder.url.setText(url);

        //create the imageloader object
        ImageLoader imageLoader = ImageLoader.getInstance();
        int defaultImage = mContext.getResources().getIdentifier("drawable://" + R.drawable.image_failed,null, mContext.getPackageName());

        //create display options
        DisplayImageOptions options = new DisplayImageOptions.Builder().cacheInMemory(true)
                .cacheOnDisc(true).resetViewBeforeLoading(true)
                .showImageForEmptyUri(defaultImage)
                .showImageOnFail(defaultImage)
                .showImageOnLoading(defaultImage).build();

        //download and display image from url
        imageLoader.displayImage(imgUrl, holder.image, options);

        return convertView;
    }

    /**
     * Required for setting up the Universal Image loader Library
     */
    private void setupImageLoader(){
        // UNIVERSAL IMAGE LOADER SETUP
        DisplayImageOptions defaultOptions = new DisplayImageOptions.Builder()
                .cacheOnDisc(true).cacheInMemory(true)
                .imageScaleType(ImageScaleType.EXACTLY)
                .displayer(new FadeInBitmapDisplayer(300)).build();

        ImageLoaderConfiguration config = new ImageLoaderConfiguration.Builder(
                mContext)
                .defaultDisplayImageOptions(defaultOptions)
                .memoryCache(new WeakMemoryCache())
                .discCacheSize(100 * 1024 * 1024).build();

        ImageLoader.getInstance().init(config);
        // END - UNIVERSAL IMAGE LOADER SETUP
    }
}
