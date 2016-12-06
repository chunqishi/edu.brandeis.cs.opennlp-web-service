package edu.brandeis.cs.lappsgrid.opennlp;

import edu.brandeis.cs.lappsgrid.Version;
import edu.brandeis.cs.lappsgrid.opennlp.api.IParser;
import opennlp.tools.cmdline.parser.ParserTool;
import opennlp.tools.parser.AbstractBottomUpParser;
import opennlp.tools.parser.Parse;
import org.lappsgrid.discriminator.Discriminators.Uri;
import org.lappsgrid.serialization.Data;
import org.lappsgrid.serialization.Serializer;
import org.lappsgrid.serialization.lif.Annotation;
import org.lappsgrid.serialization.lif.Container;
import org.lappsgrid.serialization.lif.View;
import org.lappsgrid.vocabulary.Features;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

/**
 * <i>Parser.java</i> Language Application Grids
 * (<b>LAPPS</b>)
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
public class Parser extends OpenNLPAbstractWebService implements IParser {
    protected static final Logger logger = LoggerFactory
            .getLogger(Parser.class);

    private opennlp.tools.parser.Parser parser;

    public Parser() throws OpenNLPWebServiceException {
        if (parser == null) {
            init();
            parser = loadParser(registModelMap.get(this.getClass()));
        }
    }

    private String buildPennString(Parse parses[]) {
        StringBuffer builder = new StringBuffer();
        for (Parse parse : parses) {
            parse.show(builder);
            builder.append("\n");
        }
        return builder.toString();
    }


    /* parse() is only used in test suite to see the parsing results */
    public String parse(String sentence) {
        StringBuffer builder = new StringBuffer();
        Parse parses[] = ParserTool.parseLine(sentence, parser, 1);
        System.out.println(" parses.length = " + parses.length);
        for (int pi = 0, pn = parses.length; pi < pn; pi++) {
            parses[pi].show(builder);
            builder.append("\n");
        }
        return builder.toString();
    }


    @Override
    public String execute(Container container) throws OpenNLPWebServiceException {
        String txt = container.getText();

        List<View> sentViews = container.findViewsThatContain(Uri.SENTENCE);
        if (sentViews.size() == 0) {
            throw new OpenNLPWebServiceException(String.format(
                    "Wrong Input: CANNOT find %s within previous annotations",
                    Uri.SENTENCE));
        }
        List<Annotation> sentAnns = sentViews.get(sentViews.size() - 1).getAnnotations();

        View view = container.newView();
        view.addContains(Uri.PHRASE_STRUCTURE,
                String.format("%s:%s", this.getClass().getName(), getVersion()),
                "parser:opennlp");
        view.addContains(Uri.CONSTITUENT,
                String.format("%s:%s", this.getClass().getName(), getVersion()),
                "parser:opennlp");

        view.addContains(Uri.CONSTITUENT, "parser:opennlp", this.getClass().getName() + ":" + Version.getVersion());
        view.addContains(Uri.PHRASE_STRUCTURE, "parser:opennlp", this.getClass().getName() + ":" + Version.getVersion());

        for(int sid = 0; sid < sentAnns.size(); sid++ ) {
            // for each sentence
            Annotation sentAnn = sentAnns.get(sid);
            String sentText = getTokenText(sentAnn, txt);
            Parse parses[] = ParserTool.parseLine(sentText, parser, 1);

            Annotation ps = view.newAnnotation(PS_ID + sid, Uri.PHRASE_STRUCTURE,
                    sentAnn.getStart(), sentAnn.getEnd());
            ps.addFeature("sentence", sentText);
            ps.addFeature("penntree", buildPennString(parses));
            List<String> constituentIds = new LinkedList<>();
            for (Parse parse : parses) {
                findConstituents(parse, constituentIds, sid, view);
            }
            ps.getFeatures().put(Features.PhraseStructure.CONSTITUENTS, constituentIds);
        }
        Data<Container> data = new Data<>(Uri.LIF, container);
        return Serializer.toJson(data);
    }


    protected String findConstituents(Parse parse, List<String> constituentIds, int sentId, View view) {
        String cid = CONSTITUENT_ID +sentId+"_"+ constituentIds.size();
        constituentIds.add(cid);

        Annotation constituentAnn = view.newAnnotation(cid, Uri.CONSTITUENT, -1, -1);

        if (!parse.getType().equals(AbstractBottomUpParser.TOK_NODE)) {
            constituentAnn.setLabel(parse.getType());
        } else {
            constituentAnn.setLabel(parse.getCoveredText());
        }
        if(parse.getChildren().length > 0 ) {
            List<String> children = new LinkedList<>();
            for (Parse child : parse.getChildren()) {
                String childId = findConstituents(child, constituentIds, sentId, view);
                children.add(childId);
            }
            constituentAnn.getFeatures().put("children", children.toString());
        }
        return cid;
    }
}
