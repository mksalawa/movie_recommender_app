package pl.edu.agh.movierecommender;

public class MovieCard {

    private MovieMetadata metadata;
    private String imgUrl;

    public MovieCard(MovieMetadata metadata, String imgUrl) {
        this.metadata = metadata;
        this.imgUrl = imgUrl;
    }

    public MovieMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(MovieMetadata metadata) {
        this.metadata = metadata;
    }

    public String getImgUrl() {
        return imgUrl;
    }

    public void setImgUrl(String imgUrl) {
        this.imgUrl = imgUrl;
    }
}
