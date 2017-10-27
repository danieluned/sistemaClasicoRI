

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.xml.sax.InputSource;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringBufferInputStream;
import java.io.StringReader;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/** Index all text files under a directory.
 * <p>
 * This is a command-line application demonstrating simple Lucene indexing.
 * Run it with no command-line arguments for usage information.
 */
public class IndexFiles {
  public static final String WEST = "west";
  public static final String EAST = "east";
  public static final String SOUTH = "south";
  public static final String NORTH = "north";
  
  private IndexFiles() {}

  /** Index all text files under a directory. */
  public static void main(String[] args) {
    String usage = "java org.apache.lucene.demo.IndexFiles"
                 + " [-index INDEX_PATH] [-docs DOCS_PATH] [-update]\n\n"
                 + "This indexes the documents in DOCS_PATH, creating a Lucene index"
                 + "in INDEX_PATH that can be searched with SearchFiles";
    String indexPath = "index";
    String docsPath = null;
    boolean create = true;
    for(int i=0;i<args.length;i++) {
      if ("-index".equals(args[i])) {
        indexPath = args[i+1];
        i++;
      } else if ("-docs".equals(args[i])) {
        docsPath = args[i+1];
        i++;
      } else if ("-update".equals(args[i])) {
        create = false;
      }
    }

    if (docsPath == null) {
      System.err.println("Usage: " + usage);
      System.exit(1);
    }

    final File docDir = new File(docsPath);
    if (!docDir.exists() || !docDir.canRead()) {
      System.out.println("Document directory '" +docDir.getAbsolutePath()+ "' does not exist or is not readable, please check the path");
      System.exit(1);
    }
    
    Date start = new Date();
    try {
      System.out.println("Indexing to directory '" + indexPath + "'...");

      Directory dir = FSDirectory.open(Paths.get(indexPath));
     // Analyzer analyzer = new StandardAnalyzer();
      Analyzer analyzer = new SpanishAnalyzer();
      IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

      if (create) {
        // Create a new index in the directory, removing any
        // previously indexed documents:
        iwc.setOpenMode(OpenMode.CREATE);
      } else {
        // Add new documents to an existing index:
        iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
      }

      // Optional: for better indexing performance, if you
      // are indexing many documents, increase the RAM
      // buffer.  But if you do this, increase the max heap
      // size to the JVM (eg add -Xmx512m or -Xmx1g):
      //
      // iwc.setRAMBufferSizeMB(256.0);

      IndexWriter writer = new IndexWriter(dir, iwc);
      indexDocs(writer, docDir);

      // NOTE: if you want to maximize search performance,
      // you can optionally call forceMerge here.  This can be
      // a terribly costly operation, so generally it's only
      // worth it when your index is relatively static (ie
      // you're done adding documents to it):
      //
      // writer.forceMerge(1);

      writer.close();

      Date end = new Date();
      System.out.println(end.getTime() - start.getTime() + " total milliseconds");

    } catch (IOException e) {
      System.out.println(" caught a " + e.getClass() +
       "\n with message: " + e.getMessage());
    }
  }

  /**
   * Indexes the given file using the given writer, or if a directory is given,
   * recurses over files and directories found under the given directory.
   * 
   * NOTE: This method indexes one document per input file.  This is slow.  For good
   * throughput, put multiple documents into your input file(s).  An example of this is
   * in the benchmark module, which can create "line doc" files, one document per line,
   * using the
   * <a href="../../../../../contrib-benchmark/org/apache/lucene/benchmark/byTask/tasks/WriteLineDocTask.html"
   * >WriteLineDocTask</a>.
   *  
   * @param writer Writer to the index where the given file/dir info will be stored
   * @param file The file to index, or the directory to recurse into to find files to index
   * @throws IOException If there is a low-level I/O error
   */
  static void indexDocs(IndexWriter writer, File file)
    throws IOException {
    // do not try to index files that cannot be read
    if (file.canRead()) {
      if (file.isDirectory()) {
        String[] files = file.list();
        // an IO error could occur
        if (files != null) {
          for (int i = 0; i < files.length; i++) {
            indexDocs(writer, new File(file, files[i]));
          }
        }
      } else {

        FileInputStream fis;
        try {
          fis = new FileInputStream(file);
        } catch (FileNotFoundException fnfe) {
          // at least on windows, some temporary files raise this exception with an "access denied" message
          // checking if the file can be read doesn't help
          return;
        }

        try {

          // make a new, empty document
          Document doc = new Document();

          // Add the path of the file as a field named "path".  Use a
          // field that is indexed (i.e. searchable), but don't tokenize 
          // the field into separate words and don't index term frequency
          // or positional information:
          Field pathField = new StringField("path", file.getPath(), Field.Store.YES);
          doc.add(pathField);

          // Add the last modified date of the file a field named "modified".
          // Use a LongField that is indexed (i.e. efficiently filterable with
          // NumericRangeFilter).  This indexes to milli-second resolution, which
          // is often too fine.  You could instead create a number based on
          // year/month/day/hour/minutes/seconds, down the resolution you require.
          // For example the long value 2011021714 would mean
          // February 17, 2011, 2-3 PM.
          doc.add(new LongPoint("modified", file.lastModified()));
          
          // Store the last modied date
          Date d = new Date(file.lastModified());
          Field pathField2 = new StringField("modifiedDate", d.toLocaleString(), Field.Store.YES);
          doc.add(pathField2);
          
          
          // Add the contents of the file to a field named "contents".  Specify a Reader,
          // so that the text of the file is tokenized and indexed, but not stored.
          // Note that FileReader expects the file to be in UTF-8 encoding.
          // If that's not the case searching for special characters will fail.
          doc.add(new TextField("contents", new BufferedReader(new InputStreamReader(fis, "UTF-8"))));
          
          // Documentación del DocumentBuilder https://www.tutorialspoint.com/java/xml/javax_xml_parsers_documentbuilder_inputsource.htm
          // create a new DocumentBuilderFactory
          DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

          try {
             // use the factory to create a documentbuilder
             DocumentBuilder builder = factory.newDocumentBuilder();

             // create a new document from input source
             FileInputStream fis2;
             fis2 = new FileInputStream(file);
             InputSource is = new InputSource(fis2);
             org.w3c.dom.Document doc2 = builder.parse(is);

             // obtener titulos
             camposTextField(doc,doc2,"dc:title","title");
             camposStringField(doc,doc2,"dc:identifier","identifier");
             camposTextField(doc,doc2,"dc:subject","subject");
             camposStringField(doc,doc2,"dc:type","type");
             camposTextField(doc,doc2,"dc:description","description");
             camposTextField(doc,doc2,"dc:creator","creator");
             camposTextField(doc,doc2,"dc:publisher","publisher");
             camposStringField(doc,doc2,"dc:format","format");
             camposStringField(doc,doc2,"dc:language","language");
            
             // obtener westField, eastField, northFiel, southField
             indexacionEspacial(doc, doc2);
             
             // crear indice temporal
             indexacionTemporal(doc,doc2);
             
             // crear indice y consulta para periodos temporales
             indexacionPeriodoTemporal(doc,doc2);
            
          } catch (Exception ex) {
        	  System.out.println("Error al parsear el arbol Dom");
             ex.printStackTrace();
          }
          System.out.println("paso");
          if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
            // New index, so we just add the document (no old document can be there):
            System.out.println("adding " + file);
            writer.addDocument(doc);
          } else {
            // Existing index (an old copy of this document may have been indexed) so 
            // we use updateDocument instead to replace the old one matching the exact 
            // path, if present:
            System.out.println("updating " + file);
            writer.updateDocument(new Term("path", file.getPath()), doc);
          }
          
        } finally {
          fis.close();
        }
      }
    }
  }

	private static void indexacionPeriodoTemporal(Document doc, org.w3c.dom.Document doc2) {
	// TODO Auto-generated method stub
		NodeList nodos = doc2.getElementsByTagName("dcterms:temporal");
		if (nodos.getLength() > 0){
			if (!nodos.item(0).getTextContent().equals("None") ){
				
				String a[]= nodos.item(0).getTextContent().split(";");
				if (a.length == 1){
					String begin = a[0].replaceAll("-", "");
					String end =  "20171024"; //Fecha actual
					 Field pathField = new StringField("begin",begin, Field.Store.YES);
			         doc.add(pathField);	
			         System.out.println("begin: "+begin);
			         Field pathField2 = new StringField("end",end, Field.Store.YES);
			         doc.add(pathField2);	
			         System.out.println("end: "+end);
				}else{
					
				
				String begin = a[0].split("=")[1].replaceAll("-", "");
				String end =  a[1].split("=")[1].replaceAll("-", "");
				 Field pathField = new StringField("begin",begin, Field.Store.YES);
		         doc.add(pathField);	
		         System.out.println("begin: "+begin);
		         Field pathField2 = new StringField("end",end, Field.Store.YES);
		         doc.add(pathField2);	
		         System.out.println("end: "+end);
				}
			}
			
		}
	}

	private static void indexacionTemporal(Document doc, org.w3c.dom.Document doc2) {
	// TODO Auto-generated method stub
		NodeList nodos = doc2.getElementsByTagName("dcterms:created");
		 if (nodos.getLength() > 0){
			 
			 String createdW3CDTF = nodos.item(0).getTextContent();
			 String created = createdW3CDTF.replaceAll("-", "");
			 Field pathField = new StringField("created",created, Field.Store.YES);
	         doc.add(pathField);	
	         System.out.println("created: "+created);
		 }
		 nodos = doc2.getElementsByTagName("dcterms:issued");
		 if (nodos.getLength() > 0){
			 
			 String issuedW3CDTF = nodos.item(0).getTextContent();
			 String issued = issuedW3CDTF.replaceAll("-", "");
			 Field pathField = new StringField("issued",issued, Field.Store.YES);
	         doc.add(pathField);	
	         System.out.println("issued: "+issued);
		 }
	}

	private static void indexacionEspacial(Document doc, org.w3c.dom.Document doc2) {
		NodeList nodos = doc2.getElementsByTagName("ows:LowerCorner");
		 if (nodos.getLength() > 0){
			 
			 String lowerCorner = nodos.item(0).getTextContent();
			 String lowerCornercomma = lowerCorner.replace('.',',');
			 Scanner scan = new Scanner(lowerCornercomma);
			 if(scan.hasNextDouble()){
				 Double west = scan.nextDouble();
				 DoublePoint westField = new DoublePoint(WEST,west);
				 doc.add(westField);
				 System.out.println("west: "+west);
			 }
			 if(scan.hasNextDouble()){
				 Double south = scan.nextDouble();
				 DoublePoint southField = new DoublePoint(SOUTH,south);
				 doc.add(southField);
				 System.out.println("south: "+south);
			 }
			 scan.close();
		 }
		 nodos = doc2.getElementsByTagName("ows:UpperCorner");
		 if (nodos.getLength() > 0){
			 
			 String upperCorner = nodos.item(0).getTextContent();
			 String upperCornerComma = upperCorner.replace('.',',');
			 Scanner scan = new Scanner(upperCornerComma);
			 if(scan.hasNextDouble()){
				 Double east =  scan.nextDouble();
				 DoublePoint eastField = new DoublePoint(EAST,east);
				 doc.add(eastField);
				 System.out.println("east: "+east);
			 }
			 if(scan.hasNextDouble()){
				 Double north =  scan.nextDouble();
				 DoublePoint northField = new DoublePoint(NORTH,north);
				 doc.add(northField);
				 System.out.println("north: "+north);
			 }
			 scan.close();
		 }
	}

	private static void camposStringField(Document doc, org.w3c.dom.Document doc2, String etiqueta, String campo) {
		// TODO Auto-generated method stub
		NodeList nodos = doc2.getElementsByTagName(etiqueta);
		 for (int i = 0; i < nodos.getLength(); i++) {
		     Field pathField = new StringField(campo,nodos.item(i).getTextContent(), Field.Store.YES);
	         doc.add(pathField);
		  }
	}
	
	private static void camposTextField(Document doc, org.w3c.dom.Document doc2,String etiqueta,String campo) {
		NodeList nodos = doc2.getElementsByTagName(etiqueta);
		 for (int i = 0; i < nodos.getLength(); i++) {
			 Reader inputString = new StringReader(nodos.item(i).getTextContent());
		     doc.add(new TextField(campo,new BufferedReader(inputString)));
		     
		  }
	}
}