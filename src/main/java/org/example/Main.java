package org.example;

import org.apache.lucene.queryparser.classic.ParseException;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static final String QUERY_FILE = "cran-collection\\cran.qry";

    public static void main(String[] args) throws IOException, ParseException {

        if (args.length <= 0) {
            System.out.println("Expected corpus as input");
            System.exit(1);
        }

        QueryIndex qi = new QueryIndex();
        // Build index
        qi.buildIndex(args);
        testQueries(qi);
        qi.shutdown();
    }

    public static void testQueries(QueryIndex qi) throws IOException, ParseException {
        String content = new String(Files.readAllBytes(Paths.get(QUERY_FILE)));
        String[] queries = content.split("\\.I \\d{0,4}\r\n\\.W\r\n");
        StringBuilder fileContents = new StringBuilder();
        for (int i = 0; i < queries.length; i++) {
            fileContents.append(qi.queryVSM(i, queries[i]));
        }
        try (FileWriter fw = new FileWriter("VSMqrel", false);
             PrintWriter writer = new PrintWriter(fw)) {
            writer.write(fileContents.toString());
        }
    }
}
