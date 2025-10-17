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
    private static String OUTPUT_FILENAME = "resultqrel";
    private Analyzer analyzer;
    private Directory directory;
    private static int MAX_RESULTS = 10;

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
                doc.add(new StringField("id", String.valueOf(i+1), Field.Store.YES));
                doc.add(new StringField("title", title, Field.Store.YES));
                doc.add(new StringField("author", author, Field.Store.YES));
                doc.add(new Field("content", text, ft));
                iwriter.addDocument(doc);
            }
        }
        // close the writer
        iwriter.close();
    }

    public String queryVSM(int queryId, String queryString) throws IOException, ParseException {
        if (queryString.equals("")) return "";
        // Ranks documents in order of similarity to queryString using TF-IDF (VSM)
        DirectoryReader ireader = DirectoryReader.open(directory);

        IndexSearcher isearcher = new IndexSearcher(ireader);
        isearcher.setSimilarity(new ClassicSimilarity());   // VSM
        QueryParser parser = new QueryParser("content", analyzer);
        // Analyzer doesn't remove question marks since they act as wildcards, remove manually instead
        queryString = queryString.replaceAll("\\?", "");
        // Given collection doesn't contain it, but remove asterisk as well for completeness
        queryString = queryString.replaceAll("\\*", "");
        Query queryTerm = parser.parse(queryString);
        ScoreDoc[] hits = isearcher.search(queryTerm, 20).scoreDocs;

        // Make sure we actually found something
        if (hits.length == 0)
        {
            System.out.println("Failed to retrieve a document");
            ireader.close();
            return "";
        }
        //for (ScoreDoc sd : hits) {
        //    Document doc = isearcher.doc(sd.doc);
        //    System.out.printf("DocID=%d Score=%.4f Title=%s\n",
        //            sd.doc, sd.score, doc.get("title"));
        //}
        int rank = 1;
        StringBuilder result = new StringBuilder();
        for (ScoreDoc sd: hits) {
            Document doc = isearcher.doc(sd.doc);
            int id = Integer.parseInt(doc.get("id"));
            result.append(String.format("%s Q0 %s %d %.4f lucene_runVSM\n",
                    queryId, id, rank, sd.score));
            rank++;
        }
        // close everything when we're done
        ireader.close();
        return result.toString();
    }

    public void shutdown() throws IOException {
        directory.close();
    }

}
