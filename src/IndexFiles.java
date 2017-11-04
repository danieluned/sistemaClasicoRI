

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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Paths;
import java.util.Date;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** Index all text files under a directory.
 * <p>
 * This is a command-line application demonstrating simple Lucene indexing.
 * Run it with no command-line arguments for usage information.
 */
public class IndexFiles {
  
	public static final String PATH = "path";
	
	public static final String CREATOR = "creator";
	public static final String TITLE = "title";
	public static final String IDENTIFIER = "identifier";
	public static final String SUBJECT = "subject";
	public static final String PUBLISHER = "publisher";
	public static final String DATE = "date";
	public static final String DESCRIPTION = "description";
	public static final String FORMAT = "format";
	public static final String LANGUAGE = "language";
	public static final String TYPE = "type";
	public static final String RIGHTS = "rights";
	
	public static final String CREATOR_DC = "dc:creator";
	public static final String TITLE_DC = "dc:title";
	public static final String IDENTIFIER_DC = "dc:identifier";
	public static final String SUBJECT_DC = "dc:subject";
	public static final String PUBLISHER_DC = "dc:publisher";
	public static final String DATE_DC = "dc:date";
	public static final String DESCRIPTION_DC = "dc:description";
	public static final String FORMAT_DC = "dc:format";
	public static final String LANGUAGE_DC = "dc:language";
	public static final String TYPE_DC = "dc:type";
	public static final String RIGHTS_DC = "dc:rights";
	
  private IndexFiles() {}

  /** Index all text files under a directory. */
  public static void main(String[] args) {
    String usage = "java IndexFiles"
                 + " -index <indexPath> -docs <docsPath>\n\n"
                 + "Esto indexa los documentos del directorio <docsPath> en el indice que se creara en <indexPath>.";
    
    String indexPath = null;
    String docsPath = null;

    for(int i=0;i<args.length;i++) {
      if ("-index".equals(args[i])) {
        indexPath = args[i+1];
        i++;
      } else if ("-docs".equals(args[i])) {
        docsPath = args[i+1];
        i++;
      } 
    }

    if (docsPath == null) {
      System.err.println("Uso: " + usage);
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

      // Preparar el analizador a usar
      Analyzer analyzer = new StopAnalyzer(StopWordsEs.lista());
       
      IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

     
	 // Create a new index in the directory, removing any
	 // previously indexed documents:
	 iwc.setOpenMode(OpenMode.CREATE);
     

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
      writer.forceMerge(1);

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
        Document doc = new Document();
          
          //Indice PATH
          Field pathField = new StringField(PATH, file.getName(), Field.Store.YES);
          doc.add(pathField);
          // Documentación del DocumentBuilder https://www.tutorialspoint.com/java/xml/javax_xml_parsers_documentbuilder_inputsource.htm
          // create a new DocumentBuilderFactory
          DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

          try {
             // CREAR ARBOL DOM PARA EL DOCUMENTO
             DocumentBuilder builder = factory.newDocumentBuilder();
             FileInputStream fis2;
             fis2 = new FileInputStream(file);
             InputSource is = new InputSource(fis2);
             org.w3c.dom.Document doc2 = builder.parse(is);

             //INDICES 
             indexTextField(doc, doc2, CREATOR_DC,CREATOR);
             indexTextField(doc,doc2, TITLE_DC,TITLE);
             indexStringField(doc,doc2,IDENTIFIER_DC,IDENTIFIER,false);
             indexTextField(doc,doc2,SUBJECT_DC,SUBJECT);
             indexTextField(doc,doc2,PUBLISHER_DC,PUBLISHER);
             indexNumberField(doc,doc2,DATE_DC,DATE);
             indexTextField(doc,doc2,DESCRIPTION_DC,DESCRIPTION);
             indexStringField(doc,doc2,FORMAT_DC,FORMAT,false);
             indexStringField(doc,doc2,LANGUAGE_DC,LANGUAGE,false);
             indexStringField(doc,doc2,TYPE_DC,TYPE,false);
             indexStringField(doc,doc2,RIGHTS_DC,RIGHTS,false);
             
     
          } catch (Exception ex) {
        	 // System.out.println("Error al parsear el arbol Dom");
             ex.printStackTrace();
          }
          //System.out.println("paso");
          if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
            // New index, so we just add the document (no old document can be there):
            //System.out.println("adding " + file);
            writer.addDocument(doc);
          } 
          
        } finally {
          fis.close();
        }
      }
    }
  }

 

	private static void indexNumberField(Document doc, org.w3c.dom.Document doc2, String etiqueta, String campo) {
		NodeList nodos = doc2.getElementsByTagName(etiqueta);
		for(int i = 0; i < nodos.getLength(); i++){
			String numberString = nodos.item(0).getTextContent();
			DoublePoint date = new DoublePoint(campo, Double.valueOf(numberString));
			doc.add(date);
		}
	}




	private static void indexStringField(Document doc, org.w3c.dom.Document doc2, String etiqueta, String campo,boolean separarPorEspacios) {
		// TODO Auto-generated method stub
		NodeList nodos = doc2.getElementsByTagName(etiqueta);
		 
		 for (int i = 0; i < nodos.getLength(); i++) {
			 if (separarPorEspacios){
				 String palabras[] = nodos.item(i).getTextContent().split(" ");
				 for (int j = 0; j < palabras.length; j++) {
					 Field pathField = new StringField(campo,palabras[j], Field.Store.YES);
			         doc.add(pathField);
				}
				
			 }else{
				 Field pathField = new StringField(campo,nodos.item(i).getTextContent(), Field.Store.YES);
		         doc.add(pathField);
			 }
		     
		  }
	}
	
	private static void indexTextField(Document doc, org.w3c.dom.Document doc2,String etiqueta,String campo) {
		NodeList nodos = doc2.getElementsByTagName(etiqueta);
		 for (int i = 0; i < nodos.getLength(); i++) {
			 Reader inputString = new StringReader(nodos.item(i).getTextContent());
		     doc.add(new TextField(campo,new BufferedReader(inputString)));
		     
		  }
	}









	
	


}