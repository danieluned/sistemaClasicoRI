

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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/** Simple command-line based search demo. */
public class SearchFiles {

  private SearchFiles() {}

  /** Simple command-line based search demo. */
  public static void main(String[] args) throws Exception {
    String usage =
      "Uso:\tjava SearchFiles -index <indexPath> -infoNeeds <infoNeedsFile> -output <resultsFile>\n\n."
      + "Realiza una busqueda con las necesidades <inforNeedsFile> sobre el indice <indexPath> y deja los resultados en <resultsFile>.";
    
    //Variables que se pasan por linea de comandos
    String index = null;
    String infoNeeds = null;
    String output = null;
     
    for(int i = 0;i < args.length;i++) {
      if ("-index".equals(args[i])) {
        index = args[i+1];
        i++;
      } else if ("-infoNeeds".equals(args[i])){
    	  infoNeeds = args[i+1];
    	  i++;
      } else if ("-output".equals(args[i])){
    	  output = args[i+1];
    	  i++;
      }
    }
    //Comprobar que se han introducido
    if (index == null) {
        System.err.println("Uso: " + usage);
        System.exit(1);
      }
    if (infoNeeds == null) {
        System.err.println("Uso: " + usage);
        System.exit(1);
      }
    if (output == null) {
        System.err.println("Uso: " + usage);
        System.exit(1);
      }
    

    final File infoNeedsFile = new File(infoNeeds);
    if (!infoNeedsFile.exists() || !infoNeedsFile.canRead()) {
    	System.err.println("Error: fichero de necesidades no existe o no puede ser leido.");
    	System.exit(1);
    }
    
    //Extraer las necesidades de información del fichero
    String necesidades[] = new String[numNeeds(infoNeedsFile)] , codigos[] = new String[numNeeds(infoNeedsFile)];
    extraerNecesidades(infoNeedsFile,necesidades,codigos);
    
    if(necesidades == null || necesidades.length == 0){
    	System.err.println("Error: No se han encontrado las necesidades en el fichero de necesidades.");
    	System.exit(1);
    }
    //Preparar el buscador
    IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
    IndexSearcher searcher = new IndexSearcher(reader);
    
    //Preparar el analizador
    Analyzer analyzer = new SpanishAnalyzer();
    QueryParser parser = new QueryParser("contents", analyzer);
    //Preparar el fichero de salida
    BufferedWriter bw = null;
    FileWriter fw = null;
    fw = new FileWriter(output);
    bw = new BufferedWriter(fw);
    
    //Por cada necesidad:
    for (int i = 0; i<necesidades.length; i++){
    	//Pre-procesamiento 
    	//Tokenizar, minusculas, lematizar y buscar
    	
    	//obtener resultados en lista en el fichero correspondiente
    	Query query = parser.parse(necesidades[i]);
    	TopDocs resultados = searcher.search(query,99999);
    	ScoreDoc[] hits = resultados.scoreDocs;
    	int numTotalHits = (int)resultados.totalHits;
    	//Obtener la lista de documentos y 
    	for (int j = 0; j< numTotalHits; j++){
    		Document doc = searcher.doc(hits[j].doc);
            String path = doc.get("path");
           // escribirla en el fichero de salida
            bw.write(codigos[i]+"\t"+path+"\n");
    	}
    	
    	
    	
    	
    }
    
    if (bw !=null){
    	bw.close();
    	
    }
    if (fw!=null){
    	fw.close();
    }
    reader.close();
  }

  private static int numNeeds(File infoNeedsFile){
	  DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		// use the factory to create a documentbuilder
	      DocumentBuilder builder;
			try {
				builder = factory.newDocumentBuilder();
				// create a new document from input source
				FileInputStream fis2;
				fis2 = new FileInputStream(infoNeedsFile);
				InputSource is = new InputSource(fis2);
				org.w3c.dom.Document doc2 = builder.parse(is);
				
				NodeList nodos = doc2.getElementsByTagName("informationNeed");
				return nodos.getLength();
			} catch (ParserConfigurationException | SAXException | IOException e) {
				System.err.println("Error: No se ha podido abrir el fichero de necesidades");
				
			}
			return 0;
  }
  private static void extraerNecesidades(File infoNeedsFile, String []necesidades, String []codigos) {
	// Documentación del DocumentBuilder https://www.tutorialspoint.com/java/xml/javax_xml_parsers_documentbuilder_inputsource.htm
      // create a new DocumentBuilderFactory
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
	// use the factory to create a documentbuilder
      DocumentBuilder builder;
		try {
			builder = factory.newDocumentBuilder();
			// create a new document from input source
			FileInputStream fis2;
			fis2 = new FileInputStream(infoNeedsFile);
			InputSource is = new InputSource(fis2);
			org.w3c.dom.Document doc2 = builder.parse(is);
			
			NodeList nodos = doc2.getElementsByTagName("informationNeed");
			 for (int i = 0; i < nodos.getLength(); i++) {	 
			     codigos[i] = nodos.item(i).getChildNodes().item(1).getTextContent();
			     necesidades[i] =nodos.item(i).getChildNodes().item(3).getTextContent();
			  }
		} catch (ParserConfigurationException | SAXException | IOException e) {
			System.err.println("Error: No se ha podido abrir el fichero de necesidades");
			
		}
	
  }
}