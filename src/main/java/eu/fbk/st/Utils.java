package eu.fbk.st;

import okhttp3.*;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class Utils {

    public static boolean isReadable(String dbRef, String endPoint) {
//        String url = "https://" + projectID + ".firebaseio.com/" + endPoint + ".json";
        String url = dbRef + "/" + endPoint + "/.json";
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        try  {
            Response response = client.newCall(request).execute();

            int code = response.code();
            response.body().close();

            if (code != 200) {
                return false;
//                String responseBody = response.body().string();
//
//                JSONObject jsonObject = new JSONObject(responseBody);
//                String error = jsonObject.getString("error");
//
//                if (error.equals("Permission denied")) {
//                    return false;
//                }
            }
        } catch (IOException ioe) {
            // connection problem
        } catch (JSONException je) {
            // response isn't json?
        }

        return true;
    }

    public static boolean isWritable(String dbRef, String endPoint) {
        String url = dbRef + "/" +  endPoint + "/.json";
        String jsonContent = "{ \"owaspsectest\": { \"w\": true } }";
        OkHttpClient client = new OkHttpClient();

        RequestBody body = RequestBody.create(jsonContent, MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        try  {
            Response response = client.newCall(request).execute();

            String responseBody = response.body().string();
            response.body().close();

            if (responseBody.contains("\"name\"")) {

                JSONObject jsonObject = new JSONObject(responseBody);
                String newEndPoint= jsonObject.getString("name");

                deleteEntry(dbRef, newEndPoint);


                return true;
            }
        } catch (IOException ioe) {
            // connection problem
        } catch (JSONException je) {
            // response isn't json?
        }

        return false;
    }

    public static void deleteEntry(String dbRef, String endPoint) {
        String url = dbRef + "/" +  endPoint + "./json";
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .delete()
                .build();
        try  {
            client.newCall(request).execute();
        } catch (IOException ioe) {
            // connection problem
        }
    }
}
