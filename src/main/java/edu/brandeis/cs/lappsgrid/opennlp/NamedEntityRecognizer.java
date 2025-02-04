package edu.brandeis.cs.lappsgrid.opennlp;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.util.Span;
import org.lappsgrid.discriminator.Discriminators.Uri;
import org.lappsgrid.metadata.IOSpecification;
import org.lappsgrid.metadata.ServiceMetadata;
import org.lappsgrid.serialization.Data;
import org.lappsgrid.serialization.Serializer;
import org.lappsgrid.serialization.lif.Annotation;
import org.lappsgrid.serialization.lif.Container;
import org.lappsgrid.serialization.lif.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedList;
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
public class NamedEntityRecognizer extends OpenNLPAbstractWebService {

    protected static final Logger logger = LoggerFactory.getLogger(NamedEntityRecognizer.class);

    private List<TokenNameFinder> nameFinders = new LinkedList<> ();

    public NamedEntityRecognizer() throws OpenNLPWebServiceException {
        loadAnnotators();
        this.metadata = loadMetadata();
    }

    @Override
    synchronized protected void loadAnnotators() throws OpenNLPWebServiceException {
        super.loadNameFinderModels();
        for (TokenNameFinderModel model : nameFinderModels) {
            nameFinders.add(new NameFinderME(model));
        }
    }

    public Span[] find(String[] tokens) {
        if (nameFinders.size() == 0) {
            try {
                loadAnnotators();
            } catch (OpenNLPWebServiceException e) {
                throw new RuntimeException(
                        "Fail to initialize NamedEntityRecognizer", e);
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
    public String execute(Container container) throws OpenNLPWebServiceException {
        logger.info("Executing");
        String txt = container.getText();
        List<View> tokenViews = container.findViewsThatContain(Uri.TOKEN);
        if (tokenViews.size() == 0) {
            throw new OpenNLPWebServiceException(String.format(
                    "Wrong Input: CANNOT find %s within previous annotations",
                    Uri.TOKEN));
        }
        List<Annotation> tokenAnns = tokenViews.get(tokenViews.size() - 1).getAnnotations();


        View view = container.newView();
        view.addContains(Uri.NE,
                String.format("%s:%s", this.getClass().getName(), getVersion()),
                "ner:opennlp");
        int count = 0;
        if (tokenAnns == null || tokenAnns.size() == 0)  {
            // is word.
            if (txt.matches("[a-zA-Z]+")) {
                for (TokenNameFinder nameFinder : nameFinders) {
                    Span [] neSpans = nameFinder.find(new String[]{txt});
                    for (Span span:neSpans){
                        String category = getNEType(span);
                        Annotation annotation =  view.newAnnotation(NE_ID + count++, Uri.NE, 0, txt.length());
                        annotation.addFeature("word", txt);
                        annotation.addFeature("category", category);
                    }
                }
            } else {
                throw new OpenNLPWebServiceException(String.format(
                        "Wrong Input: CANNOT find %s within previous annotations",
                        Uri.TOKEN));
            }
        } else {
            String[] tokens = new String[tokenAnns.size()];
            for(int i = 0; i < tokens.length; i++ ) {
                tokens[i] = getTokenText(tokenAnns.get(i), txt);
            }
            for (TokenNameFinder nameFinder : nameFinders) {
                Span [] namedSpans = nameFinder.find(tokens);
                for (Span span:namedSpans){
                    // namedSpans will keep all named-entities as (start_tok_id, end_tok_id) pairs
                    Long start = tokenAnns.get(span.getStart()).getStart();
                    Long end = tokenAnns.get(span.getEnd()).getEnd();
                    String category = getNEType(span);
                    Annotation ann = view.newAnnotation(NE_ID + count++, Uri.NE, start, end);
                    ann.addFeature("word", txt.substring(start.intValue(), end.intValue()));
                    ann.addFeature("category", category);
                }
            }
        }
        Data<Container> data = new Data<>(Uri.LIF, container);
        return Serializer.toJson(data);
    }

    private String getNEType(Span span) {
        String atType = null;
        switch (span.getType().toLowerCase()) {
            case "location":
                atType = Uri.LOCATION;
                break;
            case "organization":
                atType = Uri.ORGANIZATION;
                break;
            case "date":
                atType = Uri.DATE;
                break;
            case "person":
                atType = Uri.PERSON;
                break;
        }
        return atType;
    }

    public String loadMetadata() {
    	ServiceMetadata meta = new ServiceMetadata();
    	meta.setName(this.getClass().getName());
    	meta.setDescription("ner:opennlp");
    	meta.setVersion(getVersion());
    	meta.setVendor("http://www.cs.brandeis.edu/");
    	meta.setLicense(Uri.APACHE2);

    	IOSpecification requires = new IOSpecification();
    	requires.setEncoding("UTF-8");
    	requires.addLanguage("en");
    	requires.addFormat(Uri.LAPPS);
    	requires.addAnnotation(Uri.TOKEN);

    	IOSpecification produces = new IOSpecification();
    	produces.setEncoding("UTF-8");
    	produces.addLanguage("en");
    	produces.addFormat(Uri.LAPPS);
    	produces.addAnnotation(Uri.NE);

    	meta.setRequires(requires);
    	meta.setProduces(produces);
    	Data<ServiceMetadata> data = new Data<> (Uri.META, meta);
    	return data.asPrettyJson();
    }
}
