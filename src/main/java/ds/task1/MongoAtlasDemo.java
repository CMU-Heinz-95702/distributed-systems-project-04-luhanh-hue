/**
 * Author: Luhan Huang
 * AndrewID: luhanh
 * Course: 95-702 Distributed Systems
 * Project 4
 *
 * This file is part of my submission for Project .
 */
package ds.task1;

import com.mongodb.MongoSecurityException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.*;
import com.mongodb.client.model.Sorts;
import org.bson.Document;

import java.time.Instant;
import java.util.Scanner;

public class MongoAtlasDemo {

    // Prefer environment variable ATLAS_URI; otherwise fall back to your provided URI.
    // (Remove this literal and use only the env var after Task 1.)
    private static final String FALLBACK_URI =
            "mongodb+srv://luhanhuang_db_user:nX7EJuq1bufZiMPC@cluster0.004kzja.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0";

    // Database and collection names for Task 1
    private static final String DB_NAME = "cryptoapp";
    private static final String COLL_NAME = "messages";

    public static void main(String[] args) {
        String uri = System.getenv("ATLAS_URI");
        if (uri == null || uri.isBlank()) {
            uri = FALLBACK_URI; // use your provided URI if env var is not set
        }

        // Optional: allow a non-interactive run by passing the text as the first argument
        String initialTextArg = (args.length > 0) ? args[0] : null;

        try (MongoClient client = MongoClients.create(uri)) {
            MongoDatabase db = client.getDatabase(DB_NAME);
            MongoCollection<Document> col = db.getCollection(COLL_NAME);

            String text;
            if (initialTextArg != null && !initialTextArg.isBlank()) {
                text = initialTextArg;
                System.out.println("Using text from CLI arg: " + text);
            } else {
                Scanner sc = new Scanner(System.in);
                System.out.print("Enter a string to store (e.g., a crypto symbol like 'btc'): ");
                text = sc.nextLine();
            }

            Document doc = new Document("text", text)
                    .append("ts", Instant.now().toString());

            col.insertOne(doc);
            System.out.println("Inserted: " + doc.toJson());

            System.out.println("\nAll stored strings (most recent first):");
            try (MongoCursor<Document> cur = col.find()
                    .sort(Sorts.descending("ts"))
                    .iterator()) {
                while (cur.hasNext()) {
                    Document d = cur.next();
                    System.out.println("- " + d.getString("text"));
                }
            }

            System.out.println("\nDone.");
        } catch (MongoSecurityException sec) {
            System.err.println("Authentication failed. Check your username/password or connection string.");
            System.err.println("Details: " + sec.getMessage());
            System.exit(2);
        } catch (MongoTimeoutException to) {
            System.err.println("Connection timed out. Make sure your IP is allowed in Atlas Network Access and the cluster is reachable.");
            System.err.println("Details: " + to.getMessage());
            System.exit(3);
        } catch (Exception e) {
            System.err.println("Mongo error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            System.exit(4);
        }
    }
}