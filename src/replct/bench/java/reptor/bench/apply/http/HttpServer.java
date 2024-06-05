package reptor.bench.apply.http;

import reptor.distrbt.common.data.ImmutableData;
import reptor.distrbt.common.data.ImmutableDataBuffer;
import reptor.replct.service.ServiceInstance;

import java.io.*;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.StringTokenizer;

/**
 * Created by bli on 3/22/17.
 */
public class HttpServer implements ServiceInstance {

    private Map<String, String> quotes = new HashMap<String, String>();
    private String filePath = "/home/bijun/git/reptor/config/http/load.txt";
    private String line;
    private BufferedReader reader, inFromClient = null;
    private DataOutputStream outToClient = null;

    private final Random m_rand = new Random();
    private final int conflicrate = 0;

    private final String HTML_START =
            "<html>" +
                    "<title>HTTP Server in java</title>" +
                    "<body>";

    private final String HTML_END =
            "</body>" +
                    "</html>";

    public HttpServer() {
        try {
            reader = new BufferedReader(new FileReader(filePath));
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":", 2);
                if (parts.length >= 2) {
                    String book = parts[0];
                    String quote = parts[1];
                    quotes.put(book, quote);
                } else {
                    System.out.println("ignore this line: " + line);
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

//        for (String key : quotes.keySet())
//            System.out.println(key);

        try {
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public ImmutableData processCommand(int clino, ImmutableData command, boolean readonly) {
        byte[] data = null;

        try {
            data = getResponse(command.array(), readonly);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return ImmutableData.wrap(data);
    }


    public byte[] getResponse(byte[] request, boolean readonly) throws Exception {

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        inFromClient = new BufferedReader(new InputStreamReader (new ByteArrayInputStream(request)));
        outToClient = new DataOutputStream(bos);

        String requestString = inFromClient.readLine();
        String headerLine = requestString;

        StringTokenizer tokenizer = new StringTokenizer(headerLine);
        String httpMethod = tokenizer.nextToken();
        String httpQueryString = tokenizer.nextToken();

        StringBuffer responseBuffer = new StringBuffer();
        responseBuffer.append("<h1><center><u> Shakespeare\'s Plays </u></center></h1><BR>");

        if (httpMethod.equals("GET")) {
            if (httpQueryString.contains("book=")) {
                String key = (httpQueryString.substring(httpQueryString.lastIndexOf("=") + 1));
                key = URLDecoder.decode(key);
                responseBuffer.append("<h1><i><center>" + key + "</center></i></h1><BR>");
                String value = quotes.get(key);

                // Read conflicts
                int rand = m_rand.nextInt( 100 )+1;
                if (readonly && conflicrate > rand)
                {
                    String newValue = value.substring(0,value.length()-1) + String.valueOf(rand);
                    value = newValue;
                }

                if (value != null)
                {
                    responseBuffer.append("<p>" + value + "</p>");
                    sendResponse(200, responseBuffer.toString());
                }
                else {
                    responseBuffer.append("<h1> Quote: not found! </h1>");
                    sendResponse(404, responseBuffer.toString());
                }
            }
            else if (httpQueryString.contains("favicon")) {
                sendResponse(404, responseBuffer.toString());
            }
        }
        else
        {
            httpQueryString = URLDecoder.decode(httpQueryString);
            String key = httpQueryString.split("%26")[0];
            key = key.split("=")[1];
            key = key.substring(0, key.indexOf("&"));
            String value = httpQueryString.substring(httpQueryString.lastIndexOf("=") + 1);
            quotes.put(key, value);

            sendResponse(200, "<h1> Book: " + key + " updated</h1>");
        }
//        System.out.println(bos.toString());
        return bos.toByteArray();
    }

    public void sendResponse (int statusCode, String responseString) throws Exception {

        String statusLine = null;
        String serverdetails = "Server: Java HTTPServer";
        String contentLengthLine = null;
        String contentTypeLine = "Content-Type: text/html" + "\r\n";

        if (statusCode == 200)
            statusLine = "HTTP/1.1 200 OK" + "\r\n";
        else
            statusLine = "HTTP/1.1 404 Not Found" + "\r\n";
        responseString = HTML_START + responseString + HTML_END;
        contentLengthLine = "Content-Length: " + responseString.length() + "\r\n";

        outToClient.writeBytes(statusLine);
        outToClient.writeBytes(serverdetails);
        outToClient.writeBytes(contentTypeLine);
        outToClient.writeBytes(contentLengthLine);
        outToClient.writeBytes("Connection: keep-alive\r\n");
        outToClient.writeBytes("\r\n");

        outToClient.writeBytes(responseString);

        outToClient.close();
    }

    @Override
    public void applyUpdate(ImmutableData update) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableData createCheckpoint() {
        try {
            System.out.println("Create checkpoint");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutput out = new ObjectOutputStream(bos);
            out.writeObject(quotes);
            out.flush();
            out.close();
            bos.close();
            return ImmutableData.wrap(bos.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ImmutableDataBuffer(ByteBuffer.allocate(0).array());
    }

    @Override
    public void applyCheckpoint(ImmutableData checkpoint) {
        try {
            System.out.println("Apply checkpoint");
            ByteArrayInputStream bis = new ByteArrayInputStream(checkpoint.array());
            ObjectInput in = new ObjectInputStream(bis);
            quotes = (Map<String, String>) in.readObject();
            in.close();
            bis.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean createsFullCheckpoints() {
        return true;
    }
}
