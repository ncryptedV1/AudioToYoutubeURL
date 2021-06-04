package de.ncrypted.a2y;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.ResourceId;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * @author ncrypted
 */
public class AudioToYoutubeURL {

  private static String jarDir;
  private static File overview;
  private static File urls;

  private static String API_KEY;

  static {
    try {
      jarDir = new File(AudioToYoutubeURL.class.getProtectionDomain()
          .getCodeSource()
          .getLocation()
          .toURI())
          .getParentFile()
          .getPath();
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }
    overview = new File(jarDir + File.separator + "overview.txt");
    urls = new File(jarDir + File.separator + "urls.txt");
  }

  public static void main(String[] args) {
    if (args.length != 1) {
      return;
    }
    loadConfig();

    // read audio file names
    String audioDirStr = args[0];
    File audioDir = new File(audioDirStr);
    if (!audioDir.exists()) {
      System.err.println("The specified path '" + audioDirStr + "' doesnt exist");
      return;
    }
    if (!audioDir.isDirectory()) {
      System.err.println("The specified path '" + audioDirStr + "' isnt a directory");
      return;
    }
    File[] audios = audioDir.listFiles((dir, name) -> {
      if (name.endsWith(".m4a") || name.endsWith(".mp3")) {
        return true;
      }
      return false;
    });
    if (audios.length == 0) {
      System.err.println("The directory '" + audioDir.getAbsolutePath() +
          "' doesnt contain audio files in *.m4a or *.mp3 format");
      return;
    }
    overview.delete();
    urls.delete();
    BufferedWriter overviewWriter = null;
    BufferedWriter urlsWriter = null;
    try {
      overview.createNewFile();
      urls.createNewFile();
      overviewWriter = new BufferedWriter(
          new OutputStreamWriter(new FileOutputStream(overview), StandardCharsets.UTF_8));
      urlsWriter = new BufferedWriter(
          new OutputStreamWriter(new FileOutputStream(urls), StandardCharsets.UTF_8));
    } catch (IOException e) {
      e.printStackTrace();
    }

    // query file names & write results
    for (String audioName : Arrays.stream(audios)
        .map(audio -> audio.getName().substring(0, audio.getName().length() - 4))
        .collect(Collectors.toList())) {
      try {
        YoutubeSearchResult result = youtubeSearch(audioName);
        overviewWriter.write(audioName + "\t\t" + result.getUrl() + "\t\t" + result.getTitle());
        overviewWriter.newLine();
        urlsWriter.write(result.getUrl());
        urlsWriter.newLine();
      } catch (IOException e) {
        try {
          overviewWriter.write(audioName + "\t\t" + e.getMessage());
          overviewWriter.newLine();
        } catch (IOException e1) {
        }
      }
    }
    try {
      overviewWriter.close();
      urlsWriter.close();
    } catch (IOException e) {
    }
  }

  private static void loadConfig() {
    try (InputStream is = new FileInputStream("config.properties")) {
      Properties props = new Properties();
      props.load(is);

      // load config parameters
      API_KEY = props.getProperty("apiKey");
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static YoutubeSearchResult youtubeSearch(String query) throws IOException {
    YouTube youtube = new YouTube.Builder(Auth.HTTP_TRANSPORT, Auth.JSON_FACTORY, httpRequest -> {
    }).setApplicationName("AudioToYoutubeURL").build();
    YouTube.Search.List search = null;
    try {
      search = youtube.search().list("id,snippet");
    } catch (IOException e) {
      throw new IOException("An error occured");
    }
    search.setKey(API_KEY);
    search.setQ(query);
    search.setType("video");

    SearchListResponse searchResponse = search.execute();
    List<SearchResult> results = searchResponse.getItems();
    if (results.isEmpty()) {
      throw new IOException("No video found");
    }
    SearchResult firstResult = results.get(0);
    ResourceId rId = firstResult.getId();
    return new YoutubeSearchResult("https://www.youtube.com/watch?v=" + rId.getVideoId(),
        firstResult.getSnippet().getTitle());
  }

  private static class YoutubeSearchResult {
    private String url;
    private String title;

    public YoutubeSearchResult(String url, String title) {
      this.url = url;
      this.title = title;
    }

    public String getUrl() {
      return url;
    }

    public String getTitle() {
      return title;
    }
  }
}
