package edu.brandeis.cs.lappsgrid.opennlp;

import edu.brandeis.cs.lappsgrid.Version;
import edu.brandeis.cs.lappsgrid.api.opennlp.INamedEntityRecognizer;
import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.util.Span;
import org.lappsgrid.discriminator.Discriminators;
import org.lappsgrid.serialization.json.JsonObj;
import org.lappsgrid.serialization.json.LIFJsonSerialization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * <i>NamedEntityRecognizer.java</i> Language Application Grids (<b>LAPPS</b>)
 * <p>
 * <p>
 * <a href="http://opennlp.sourceforge.net/models-1.5/">Models for 1.5
 * series</a>
 * <p>
 * 
 * @author Chunqi Shi ( <i>shicq@cs.brandeis.edu</i> )<br>
 *         Nov 20, 2013<br>
 * 
 */
public class NamedEntityRecognizer extends OpenNLPAbstractWebService implements INamedEntityRecognizer {
	protected static final Logger logger = LoggerFactory
			.getLogger(NamedEntityRecognizer.class);

	private static ArrayList<TokenNameFinder> nameFinders = new ArrayList<TokenNameFinder> ();
	
	String metadata;
	
	public NamedEntityRecognizer() throws OpenNLPWebServiceException {
        if (nameFinders.size() == 0) {
            super.init();
            nameFinders.addAll(loadTokenNameFinders(registModelMap.get(this.getClass())).values());
            this.metadata = loadMetadata();
        }
	}

    public static String capitalize(String s) {
        if (s == null || s.length() == 0) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

	public Span[] find(String[] tokens) {
		if (nameFinders.size() == 0) {
			try {
				init();
			} catch (OpenNLPWebServiceException e) {
				throw new RuntimeException(
						"tokenize(): Fail to initialize NamedEntityRecognizer", e);
			}
		}
		ArrayList<Span> spanArr = new ArrayList<Span>(16);
		for (TokenNameFinder nameFinder : nameFinders) {
			Span[] partSpans = nameFinder.find(tokens);
			for (Span span:partSpans)
				spanArr.add(span);
		}
		
		return spanArr.toArray(new Span[spanArr.size()]);
	}

    @Override
    public String execute(LIFJsonSerialization json) throws OpenNLPWebServiceException {
        logger.info("execute(): Execute OpenNLP ner ...");
        String txt = json.getText();
        List<JsonObj> tokenObjs = json.getLastViewAnnotations(Discriminators.Uri.TOKEN);

        JsonObj view = json.newView();
        json.newContains(view, Discriminators.Uri.NE,
                "ner:opennlp", this.getClass().getName() + ":" + Version.getVersion());
        json.setIdHeader("tok");
        int cnt = 0;
        if (tokenObjs == null || tokenObjs.size() == 0)  {
            // is word.
            if (txt.matches("[a-zA-Z]+")) {
                for (TokenNameFinder nameFinder : nameFinders) {
                    Span [] partSpans = nameFinder.find(new String[]{txt});
                    for (Span span:partSpans){
                        JsonObj annotation =  json.newAnnotation(view);
                        json.setId(annotation, "ne"+cnt++);
//                        json.setType(annotation, Discriminators.Uri.NE);
                        json.setStart(annotation, 0);
                        json.setEnd(annotation, txt.length());
                        json.setWord(annotation,txt);
//                        json.setCategory(annotation, span.getType());
                        String atType = null;
                        switch (span.getType().toLowerCase()) {
                            case "location":
                                atType = Discriminators.Uri.LOCATION;
                                break;
                            case "organizatoin":
                                atType = Discriminators.Uri.ORGANIZATION;
                                break;
                            case "date":
                                atType = Discriminators.Uri.DATE;
                                break;
                            case "person":
                                atType = Discriminators.Uri.PERSON;
                                break;
                            default:
                                System.out.println("++++++++++++++++++++++++++++++");
                                System.out.println(span.getType());
                                System.out.println("++++++++++++++++++++++++++++++");
                        }
                        json.setType(annotation, atType);
                    }
                }
            } else {
                throw new OpenNLPWebServiceException(String.format(
                        "Wrong Input: CANNOT find %s within previous annotations",
                        Discriminators.Uri.TOKEN));
            }
        } else {
            String[] tokens = new String[tokenObjs.size()];
            for(int i = 0; i < tokens.length; i++ ) {
                tokens[i] = json.getAnnotationText(tokenObjs.get(i));
            }
            for (TokenNameFinder nameFinder : nameFinders) {
                Span [] partSpans = nameFinder.find(tokens);
                for (Span span:partSpans){
                    JsonObj org = tokenObjs.get(span.getStart());
                    JsonObj annotation = json.newAnnotation(view, org);
                    String atType = null;
                    switch (span.getType().toLowerCase()) {
                        case "person": atType = Discriminators.Uri.PERSON;
                            break;
                        case "location": atType = Discriminators.Uri.LOCATION;
                            break;
                        case "date": atType = Discriminators.Uri.DATE;
                            break;
                        case "organization": atType = Discriminators.Uri.ORGANIZATION;
                            break;
                    }
                    json.setType(annotation, atType);
                    json.setWord(annotation, json.getAnnotationText(annotation));
//                    json.setCategory(annotation, span.getType());
                }
            }
        }
        return json.toString();
    }
    
    public String loadMetadata() {
    	ServiceMetadata meta = new ServiceMetadata();
    	meta.setName(this.getClass().getName());
    	meta.setDescription("ner:opennlp");
    	meta.setVersion(Version.getVersion());
    	meta.setVendor("http://www.cs.brandeis.edu/");
    	meta.setLicense(Discriminators.Uri.APACHE2);
    	
    	IOSpecification requires = new IOSpecification();
    	requires.setEncoding("UTF-8");
    	requires.addLanguage("en");
    	requires.addFormat(Discriminators.Uri.LAPPS);
    	requires.addAnnotation(Discriminators.Uri.TOKEN);
    	
    	IOSpecification produces = new IOSpecification();
    	produces.setEncoding("UTF-8");
    	produces.addLanguage("en");
    	produces.addFormat(Discriminators.Uri.LAPPS);
    	produces.addAnnotation(Discriminators.Uri.NE);
    	
    	meta.setRequires(requires);
    	meta.setProduces(produces);
    	Data<ServiceMetadata> data = new Data<> (Discriminators.Uri.META, meta);
    	return data.asPrettyJson();
    }
    
    public String getMetadata() {
    	return this.metadata;
    }
    
}
