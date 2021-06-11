package pl.edu.agh.movierecommender;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

public class CollabFragment extends Fragment {

    private ListView collabMovieListView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_collab, container, false);
//        collabMovieListView = view.findViewById(R.id.listViewForCollab);

//        MovieListAdapter adapter = new MovieListAdapter(getActivity(), R.layout.card_view_layout, list);
//        collabMovieListView.setAdapter(adapter);
        return view;
    }

}
