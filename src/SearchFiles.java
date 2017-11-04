

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/** Simple command-line based search demo. */
public class SearchFiles {

	// Aumentar la puntuación de un documento dependiendo de donde se haya encontrado la información
	// cada asterisco representa mayor importancia
	static final float CREATOR_BOOST = 3.5f;					
	static final float TITLE_BOOST = 2.5f;				
	static final float IDENTIFIER_BOOST = 1.9f;			
	static final float SUBJECT_BOOST = 2.6f;			
	static final float PUBLISHER_BOOST = 1.1f;						
	static final float DESCRIPTION_BOOST = 1.2f;		
	static final float FORMAT_BOOST = 1.0f;				
	static final float LANGUAGE_BOOST = 1.0f;			
	static final float TYPE_BOOST = 1.0f;				
	static final float RIGHTS_BOOST = 1.0f;				
	
	// Date
	static final float DATE_MIN = 1.2f;					
	static final float DATE_MAX = 1.2f;					
	
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
    
    //Preparar el analizador para los textos
    Analyzer stopAnalyzer = new StopAnalyzer(StopWordsEs.lista());
    //Analizar basico para los campos simples
    Analyzer whiteSpaceAnalyzer = new WhitespaceAnalyzer();
    
    //Preparar el fichero de salida
    BufferedWriter bw = null;
    FileWriter fw = null;
    fw = new FileWriter(output);
    bw = new BufferedWriter(fw);
    
    //Por cada necesidad:
    for (int i = 0; i<necesidades.length; i++){
    	
    	
    	// Creator	(text)
    	BoostQuery creator 		= boostQuery(necesidades[i],IndexFiles.CREATOR,CREATOR_BOOST, stopAnalyzer);
    	// Title	(text)
    	BoostQuery title 		= boostQuery(necesidades[i],IndexFiles.TITLE,TITLE_BOOST, stopAnalyzer);
        // identifier  (String)
    	BoostQuery identifier 	= boostQuery(necesidades[i],IndexFiles.IDENTIFIER,IDENTIFIER_BOOST, whiteSpaceAnalyzer);
    	// subject (String)
    	BoostQuery subject 		= boostQuery(necesidades[i],IndexFiles.SUBJECT,SUBJECT_BOOST, stopAnalyzer);
    	// publisher
    	BoostQuery publisher 	= boostQuery(necesidades[i],IndexFiles.PUBLISHER,PUBLISHER_BOOST, stopAnalyzer);
    	// description
    	BoostQuery description 	= boostQuery(necesidades[i],IndexFiles.DESCRIPTION,DESCRIPTION_BOOST, stopAnalyzer);
    	// format	
    	BoostQuery format		 = boostQuery(necesidades[i],IndexFiles.FORMAT,FORMAT_BOOST, whiteSpaceAnalyzer);
    	// language
    	BoostQuery language		 = boostQuery(necesidades[i],IndexFiles.LANGUAGE,LANGUAGE_BOOST, whiteSpaceAnalyzer);
    	// type 
    	BoostQuery type			 = boostQuery(necesidades[i],IndexFiles.TYPE,TYPE_BOOST, whiteSpaceAnalyzer);
    	// rights
    	BoostQuery rights		 = boostQuery(necesidades[i],IndexFiles.RIGHTS,RIGHTS_BOOST, whiteSpaceAnalyzer);
    	
 
    	//IDENTIFICAR SI SE ESTA EXPRESANDO UNA FECHA RESTRICTIVA
    	Double fechaini = null;
    	Double fechafin = null;
    	String  exp= "a partir del [0-9]{4}";
    	Pattern pattern = Pattern.compile(exp);
    	Matcher m = pattern.matcher(necesidades[i]);
    	if (m.find()){
    		
    		//Añadir query fecha de inicio
    		String frase = m.group();
    		fechaini = Double.valueOf(frase.substring(13));
    	}
    	
    	exp = "hasta [0-9]{4}";
    	pattern = Pattern.compile(exp);
    	m = pattern.matcher(necesidades[i]);
    	if (m.find()){
    		//Añadir query fecha final
    		String frase = m.group();
    		fechafin = Double.valueOf(frase.substring(6));
    	}
    	
    	exp = "[0-9]{4} y [0-9]{4}";
    	pattern = Pattern.compile(exp);
    	m = pattern.matcher(necesidades[i]);
    	if(m.find()){
    		//Añadir query con fecha inicial y final
    		String frase = m.group();
    		fechaini = Double.valueOf(frase.substring(0,4));
    		fechafin = Double.valueOf(frase.substring(7));
    	}
    	//System.out.println("Fechaini: "+fechaini+" Fechafin: "+fechafin);
    	//fechaini <= date 
    	Query querydateIni = null;
    	BoostQuery boostdateIni = null;
    	if (fechaini != null){
    		querydateIni = DoublePoint.newRangeQuery(IndexFiles.DATE, fechaini, Double.POSITIVE_INFINITY);
    		boostdateIni = new BoostQuery(querydateIni,2.0f);
    	}
    	Query querydateFin = null;
    	BoostQuery boostdateFin = null;
    	if (fechafin !=null){
    		querydateFin = DoublePoint.newRangeQuery(IndexFiles.DATE, Double.NEGATIVE_INFINITY,fechafin);
    		boostdateFin = new BoostQuery(querydateFin,2.0f);
    		
    	}
    	  	   	
    	//Mezclar queries con metodo booleano
    	Builder builderobli = new BooleanQuery.Builder();
    	//OBLIGATORIAS EN ALGUNA DE ESTAS
    	
    	//title			
    	builderobli.add(title,BooleanClause.Occur.SHOULD);
    	
    	//description
    	builderobli.add(description,BooleanClause.Occur.SHOULD);
    	///subject
    	builderobli.add(subject,BooleanClause.Occur.SHOULD);
    	
    	
    	
    	//creacion de la consulta obligatoria
    	BooleanQuery obligatorias = builderobli.build();
    	Builder builderquery = new BooleanQuery.Builder();
    	builderquery.add(obligatorias,BooleanClause.Occur.MUST);
    	
    	//OPCIONAL EN LAS SIGUIENTES
    	
    	//identifier
    	builderquery.add(identifier,BooleanClause.Occur.SHOULD);
    	//creator
    	builderquery.add(creator,BooleanClause.Occur.SHOULD);
    	//publisher
    	builderquery.add(publisher,BooleanClause.Occur.SHOULD);
    	//format
    	builderquery.add(format,BooleanClause.Occur.SHOULD);
    	//language
    	builderquery.add(language,BooleanClause.Occur.SHOULD);
    	//type
    	builderquery.add(type,BooleanClause.Occur.SHOULD);
    	//rights
    	builderquery.add(rights,BooleanClause.Occur.SHOULD);
    	
    	// date
    	if (boostdateIni != null){
    		builderquery.add(boostdateIni,BooleanClause.Occur.SHOULD);
    	}
    	if(boostdateFin != null){
    		builderquery.add(boostdateFin,BooleanClause.Occur.SHOULD);
    	}
    	
    	//Crear query
    	BooleanQuery query = builderquery.build();
    	
    	//Combinar
    	
    	
    	//Buscar en el indice con la query obtenida 
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

private static BoostQuery boostQuery(String necesidad,String campo ,float boost,Analyzer analyzer) throws ParseException {
	QueryParser qparser = new QueryParser(campo, analyzer);
	Query qqcreator = qparser.parse(necesidad);
	return new BoostQuery(qqcreator,boost);
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