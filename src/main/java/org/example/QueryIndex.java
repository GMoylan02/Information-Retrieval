package org.example;

import java.io.FileWriter;
import java.io.IOException;

import java.io.PrintWriter;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.Objects;
import java.util.Scanner;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.util.BytesRef;

public class QueryIndex
{
    // Directory where the search index will be saved
    private static String INDEX_DIRECTORY = "index";
    private Analyzer analyzer;
    private Directory directory;
    private static int MAX_RESULTS = 50;
    private static final int VSM_MODE = 1;
    private static final int BM25_MODE = 2;

    public QueryIndex() throws IOException {
        // Incorporates porter stemming plus stop words plus a few others
        analyzer = new EnglishAnalyzer();
        directory = FSDirectory.open(Paths.get(INDEX_DIRECTORY));
    }

    public void buildIndex(String[] args) throws IOException {

        // Create a new field type which will store term vector information
        FieldType ft = new FieldType(TextField.TYPE_STORED);
        ft.setTokenized(true); //done as default
        ft.setStoreTermVectors(true);
        ft.setStoreTermVectorPositions(true);
        ft.setStoreTermVectorOffsets(true);
        ft.setStoreTermVectorPayloads(true);

        // create and configure an index writer
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        IndexWriter iwriter = new IndexWriter(directory, config);

        // Add all input documents to the index
        for (String arg : args) {
            System.out.printf("Indexing \"%s\"\n", arg);
            String content = new String(Files.readAllBytes(Paths.get(arg)));
            String[] docs = content.split("\\.I");
            for (int i = 0; i < docs.length; i++) {
                docs[i] = docs[i].trim();
                if (docs[i].isEmpty()) continue;
                String title = docs[i].split("\\.T\\n")[1].split("\\n\\.A\\n")[0];
                String author = docs[i].split("\\.A\\n")[1].split("\\n\\.B\\n")[0];
                String[] docSplit = docs[i].split("\\.W\\n");
                if (docSplit.length < 2) {
                    continue;
                }
                String text = docSplit[1];
                Document doc = new Document();

                doc.add(new StringField("id", String.valueOf(i), Field.Store.YES));
                doc.add(new TextField("title", title, Field.Store.YES));
                doc.add(new TextField("author", author, Field.Store.YES));
                String fullText = title + " " + author + " " + text;
                doc.add(new TextField("content", fullText, Field.Store.YES));
                iwriter.addDocument(doc);
            }
        }
        // close the writer
        iwriter.close();
    }

    public String query(int queryId, String queryString, int mode) throws IOException, ParseException {
        if (queryString.equals("")) return "";
        // Ranks documents in order of similarity to queryString using TF-IDF (VSM)
        DirectoryReader ireader = DirectoryReader.open(directory);

        IndexSearcher isearcher = new IndexSearcher(ireader);
        // TODO fix this
        if (mode == VSM_MODE) {
            isearcher.setSimilarity(new ClassicSimilarity());   // VSM
        }
        else {
            isearcher.setSimilarity(new BM25Similarity()); // BM25
        }
        QueryParser parser = new QueryParser("content", analyzer);
        // Change everything to lowercase and remove special characters
        queryString = queryString.toLowerCase().replaceAll("[^a-z0-9\\s]", " ");
        Query queryTerm = parser.parse(queryString);
        ScoreDoc[] hits = isearcher.search(queryTerm, MAX_RESULTS).scoreDocs;

        // Make sure we actually found something
        if (hits.length == 0)
        {
            System.out.println("Failed to retrieve a document");
            ireader.close();
            return "";
        }
        String modeString = mode == VSM_MODE ? "VSM" : "BM25";
        int rank = 1;
        StringBuilder result = new StringBuilder();
        for (ScoreDoc sd: hits) {
            Document doc = isearcher.doc(sd.doc);
            int id = Integer.parseInt(doc.get("id"));
            result.append(String.format("%s Q0 %s %d %.4f lucene_run%s\n",
                    queryId, id, rank, sd.score, modeString));
            rank++;
        }

        ireader.close();
        return result.toString();
    }

    public void shutdown() throws IOException {
        directory.close();
    }

}
