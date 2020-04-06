package tests;

import com.sun.org.apache.xpath.internal.axes.FilterExprWalker;
import grammar.AST;
import grammar.Template3Parser;
import grammar.cfg.CFGBuilder;
import grammar.transformations.util.StanFileWriter;
import grammar.transformations.util.SampleToTarget;
import grammar.transformations.util.StanFileWriter;
import grammar.transformations.util.TransWriter;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.TokenStreamRewriter;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.apache.commons.io.FileUtils;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import grammar.transformations.*;
import org.junit.rules.TemporaryFolder;
import translators.Stan2IRTranslator;
import translators.StanTranslator;
import translators.listeners.CFGWalker;
import grammar.transformations.util.ObserveToLoop;
import utils.Utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

public class TestTransformer {

    @Test
    @Ignore
    public void TestNormal2T(){
        CFGBuilder cfgBuilder = new CFGBuilder("/home/zixin/Documents/are/PPVM/templates/basic/basic.template", null, false);

        TransformController transformController = new TransformController(cfgBuilder.getSections());
        try {
            transformController.analyze();
            transformController.transform();
            StanTranslator stanTranslator = new StanTranslator();
            stanTranslator.translate(cfgBuilder.getSections());
            stanTranslator.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void TestTransformers(){
        CFGBuilder cfgBuilder = new CFGBuilder("src/test/resources/poisson.template", null, false);

        TransformController transformController = new TransformController(cfgBuilder.getSections());
        try {
            transformController.analyze();
            transformController.transform();
            StanTranslator stanTranslator = new StanTranslator();
            stanTranslator.translate(cfgBuilder.getSections());
            stanTranslator.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    @Ignore
    public void TestUndo(){
        CFGBuilder cfgBuilder = new CFGBuilder("src/test/resources/basic.template", null, false);

        TransformController transformController = new TransformController(cfgBuilder.getSections());
        try {
            transformController.analyze();
            transformController.transform();
            transformController.undoAll();
            StanTranslator stanTranslator = new StanTranslator();
            stanTranslator.translate(cfgBuilder.getSections());
            stanTranslator.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void TestUndoOne(){
        CFGBuilder cfgBuilder = new CFGBuilder("src/test/resources/basic_robust4.template", null, false);

        TransformController transformController = new TransformController(cfgBuilder.getSections());
        try {
            transformController.analyze();
            transformController.transform_one();
            transformController.undo();
            transformController.transform_one();
            StanTranslator stanTranslator = new StanTranslator();
            stanTranslator.translate(cfgBuilder.getSections());
            stanTranslator.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void ObserveToLoop(){

        try {
            Stan2IRTranslator stan2IRTranslator =
                    new Stan2IRTranslator("src/test/resources/stan/radon.pooling.stan",
                            "src/test/resources/stan/radon.pooling.data.R");
            String code = stan2IRTranslator.getCode();
            System.out.println(code);
            File file = temporaryFolder.newFile();
            FileUtils.writeStringToFile(file, code);
            System.out.println(code);
            CFGBuilder cfgBuilder = new CFGBuilder(file.getAbsolutePath(), null);

            TokenStreamRewriter antlrRewriter = new TokenStreamRewriter(cfgBuilder.parser.getTokenStream());
            ObserveToLoop observeToLoop = new ObserveToLoop(cfgBuilder, antlrRewriter);
            ParseTreeWalker walker = new ParseTreeWalker();
            // CFGWalker cfgWalker = new CFGWalker(cfgBuilder.getSections(), observeToLoop);
            // cfgWalker.walk();
            Template3Parser parser = cfgBuilder.parser;
            parser.reset();
            walker.walk(observeToLoop, parser.template());
            String templateCode = antlrRewriter.getText();
            System.out.println(templateCode);

            File transfile = temporaryFolder.newFile();
            FileUtils.writeStringToFile(transfile, templateCode);
            StanTranslator stanTranslator = new StanTranslator();
            stanTranslator.translate((new CFGBuilder(transfile.getAbsolutePath(), null)).getSections());
            System.out.println(stanTranslator.getCode());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
    @Test
    public void TestObserveToLoop2(){
        try {
            CFGBuilder cfgBuilder = new CFGBuilder("src/test/resources/poisson.template", null);
            TokenStreamRewriter antlrRewriter = new TokenStreamRewriter(cfgBuilder.parser.getTokenStream());
            ObserveToLoop observeToLoop = new ObserveToLoop(cfgBuilder, antlrRewriter);

            ParseTreeWalker walker = new ParseTreeWalker();
            Template3Parser parser = cfgBuilder.parser;
            parser.reset();
            walker.walk(observeToLoop, parser.template());
            String templateCode = antlrRewriter.getText();
            System.out.println(templateCode);

            File transfile = temporaryFolder.newFile();
            FileUtils.writeStringToFile(transfile, templateCode);
            StanTranslator stanTranslator = new StanTranslator();
            stanTranslator.translate((new CFGBuilder(transfile.getAbsolutePath(), null)).getSections());
            System.out.println(stanTranslator.getCode());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Test
    public void TestSampleToTarget(){
        try {
            CFGBuilder cfgBuilder = new CFGBuilder("src/test/resources/poisson.template", null);
            TokenStreamRewriter antlrRewriter = new TokenStreamRewriter(cfgBuilder.parser.getTokenStream());
            ObserveToLoop observeToLoop = new ObserveToLoop(cfgBuilder, antlrRewriter);
            SampleToTarget sampleToTarget = new SampleToTarget(cfgBuilder, antlrRewriter);

            ParseTreeWalker walker = new ParseTreeWalker();
            Template3Parser parser = cfgBuilder.parser;
            parser.reset();
            walker.walk(observeToLoop, parser.template());
            parser.reset();
            walker.walk(sampleToTarget, parser.template());
            String templateCode = antlrRewriter.getText();
            System.out.println(templateCode);

            File transfile = temporaryFolder.newFile();
            FileUtils.writeStringToFile(transfile, templateCode);
            StanTranslator stanTranslator = new StanTranslator();
            stanTranslator.translate((new CFGBuilder(transfile.getAbsolutePath(), null)).getSections());
            System.out.println(stanTranslator.getCode());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
    @Test
    public void TestReweighter(){
        try {
            CFGBuilder cfgBuilder = new CFGBuilder("src/test/resources/poisson.template", null);
            TokenStreamRewriter antlrRewriter = new TokenStreamRewriter(cfgBuilder.parser.getTokenStream());
            ObserveToLoop observeToLoop = new ObserveToLoop(cfgBuilder, antlrRewriter);
            SampleToTarget sampleToTarget = new SampleToTarget(cfgBuilder, antlrRewriter);

            ParseTreeWalker walker = new ParseTreeWalker();
            Template3Parser parser = cfgBuilder.parser;
            parser.reset();
            walker.walk(observeToLoop, parser.template());
            parser.reset();
            walker.walk(sampleToTarget, parser.template());



            String templateCode = antlrRewriter.getText();
            File tempfile = temporaryFolder.newFile();
            FileUtils.writeStringToFile(tempfile, templateCode);
            cfgBuilder = new CFGBuilder(tempfile.getAbsolutePath(), null);
            parser = cfgBuilder.parser;
            antlrRewriter = new TokenStreamRewriter(parser.getTokenStream());
            Reweighter reweighter = new Reweighter(cfgBuilder, antlrRewriter);
            parser.reset();
            walker.walk(reweighter, parser.template());
            templateCode = antlrRewriter.getText();
            System.out.println(templateCode);

            tempfile = temporaryFolder.newFile();
            FileUtils.writeStringToFile(tempfile, templateCode);
            StanTranslator stanTranslator = new StanTranslator();
            stanTranslator.translate((new CFGBuilder(tempfile.getAbsolutePath(), null)).getSections());
            System.out.println(stanTranslator.getCode());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
    @Test
    public void TestReweighter2(){
        try {
            Stan2IRTranslator stan2IRTranslator =
                    new Stan2IRTranslator("src/test/resources/stan/radon.pooling.stan",
                            "src/test/resources/stan/radon.pooling.data.R");
            String code = stan2IRTranslator.getCode();
            System.out.println(code);
            File file = temporaryFolder.newFile();
            FileUtils.writeStringToFile(file, code);
            System.out.println(code);
            CFGBuilder cfgBuilder = new CFGBuilder(file.getAbsolutePath(), null, false);
            TokenStreamRewriter antlrRewriter = new TokenStreamRewriter(cfgBuilder.parser.getTokenStream());
            ObserveToLoop observeToLoop = new ObserveToLoop(cfgBuilder, antlrRewriter);
            SampleToTarget sampleToTarget = new SampleToTarget(cfgBuilder, antlrRewriter);

            ParseTreeWalker walker = new ParseTreeWalker();
            Template3Parser parser = cfgBuilder.parser;
            parser.reset();
            walker.walk(observeToLoop, parser.template());
            parser.reset();
            walker.walk(sampleToTarget, parser.template());



            String templateCode = antlrRewriter.getText();
            File tempfile = temporaryFolder.newFile();
            FileUtils.writeStringToFile(tempfile, templateCode);
            cfgBuilder = new CFGBuilder(tempfile.getAbsolutePath(), null, false);
            parser = cfgBuilder.parser;
            antlrRewriter = new TokenStreamRewriter(parser.getTokenStream());
            Reweighter reweighter = new Reweighter(cfgBuilder, antlrRewriter);
            parser.reset();
            walker.walk(reweighter, parser.template());
            templateCode = antlrRewriter.getText();
            System.out.println(templateCode);

            tempfile = temporaryFolder.newFile();
            FileUtils.writeStringToFile(tempfile, templateCode);
            StanTranslator stanTranslator = new StanTranslator();
            stanTranslator.translate((new CFGBuilder(tempfile.getAbsolutePath(), null, false)).getSections());
            System.out.println(stanTranslator.getCode());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Test
    public void TestWhatever() throws Exception {
        TransWriter transWriter = new TransWriter("src/test/resources/stan/radon.pooling.stan",
                            "src/test/resources/stan/radon.pooling.data.R");
        transWriter.transformObserveToLoop();
        transWriter.transformSampleToTarget();
        transWriter.transformReweighter();
        System.out.println(transWriter.getStanCode());
    }

    @Test
    public void TestPred() throws Exception {
        try {
            TransWriter transWriter = new TransWriter("src/test/resources/stan/radon.pooling.stan",
                    "src/test/resources/stan/radon.pooling.data.R");
            transWriter.transformObserveToLoop();
            transWriter.transformSampleToTarget();
            transWriter.transformOrgPredCode();
            System.out.println(transWriter.getPredCode());
            System.out.println(transWriter.getStanPredCode());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void TestAddPred() throws Exception {
        try {
//            TransWriter transWriter = new TransWriter("src/test/resources/stan/radon.pooling.stan",
//                   "src/test/resources/stan/radon.pooling.data.R");
//             TransWriter transWriter = new TransWriter("src/test/resources/poisson.template");
             TransWriter transWriter = new TransWriter("src/test/resources/stan/logistic.stan",
                     "src/test/resources/stan/logistic.data.R");
            transWriter.transformObserveToLoop();
            transWriter.transformSampleToTarget();
            transWriter.setReuseCode();
            transWriter.transformOrgPredCode();
            Boolean transformed;

            // ConstToParam
            System.out.println("========ConstToParam========");
            transWriter.resetCode();
            transformed = transWriter.transformConstToParam();
            if (transformed)
                transWriter.addPredCode(transWriter.getCode(), "robust_const");


            // Reweighter
            System.out.println("========Reweighting========");
            transWriter.resetCode();
            transWriter.transformReweighter();
            transWriter.addPredCode(transWriter.getCode(), "robust_reweight");

            // Localizer
            Boolean existNext = true;
            int paramCount = 0;
            while (existNext){
                System.out.println("========Localizing Param " + paramCount + "========");
                transWriter.resetCode();
                existNext = transWriter.transformLocalizer(paramCount);
                transWriter.addPredCode(transWriter.getCode(), "robust_local" + (1 + paramCount));
                paramCount++;
            }

            // Reparam, Normal2T
            System.out.println("========Reparam:Normal2T========");
            transWriter.resetCode();
            transformed = transWriter.transformReparamLocalizer();
            if (transformed)
                transWriter.addPredCode(transWriter.getCode(), "robust_reparam");

            // Logit
            System.out.println("========Logit========");
            transWriter.resetCode();
            transformed = transWriter.transformLogit();
            if (transformed)
                transWriter.addPredCode(transWriter.getCode(), "robust_logit");

            // MixNormal
            System.out.println("========MixNormal========");
            transWriter.resetCode();
            transformed = transWriter.transformMixNormal();
            if (transformed)
                transWriter.addPredCode(transWriter.getCode(), "robust_mix");

            System.out.println(transWriter.getPredCode());
            System.out.println(transWriter.getStanPredCode());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Test
    public void TestLocalizer() throws Exception {
        TransWriter transWriter = new TransWriter("src/test/resources/stan/radon.pooling.stan",
                "src/test/resources/stan/radon.pooling.data.R");
        transWriter.transformObserveToLoop();
        transWriter.transformSampleToTarget();
        transWriter.setReuseCode();
        Boolean existNext = true;
        int paramCount = 0;
        while (existNext){
            System.out.println("========Localizing Param " + paramCount + "========");
            transWriter.resetCode();
            existNext = transWriter.transformLocalizer(paramCount);
            System.out.println(transWriter.getCode());
            System.out.println(transWriter.getStanCode());
            paramCount++;
        }
    }

    @Test
    public void TestReparamLocalizer() throws Exception {
        TransWriter transWriter = new TransWriter("src/test/resources/stan/radon.pooling.stan",
                "src/test/resources/stan/radon.pooling.data.R");
        transWriter.transformObserveToLoop();
        transWriter.transformSampleToTarget();
        Boolean transformed = transWriter.transformReparamLocalizer();
        System.out.println("Find Normal? " + transformed);
        System.out.println(transWriter.getCode());
        System.out.println(transWriter.getStanCode());
    }

    @Test
    public void TestLogit() throws Exception {
        TransWriter transWriter = new TransWriter("src/test/resources/stan/logistic.stan",
                "src/test/resources/stan/logistic.data.R");
        transWriter.transformObserveToLoop();
        transWriter.transformSampleToTarget();
        Boolean transformed = transWriter.transformLogit();
        System.out.println("Find Logit? " + transformed);
        System.out.println(transWriter.getCode());
        System.out.println(transWriter.getStanCode());
    }

    @Test
    public void TestMixNormal() throws Exception {
        TransWriter transWriter = new TransWriter("src/test/resources/stan/radon.pooling.stan",
                "src/test/resources/stan/radon.pooling.data.R");
        transWriter.transformObserveToLoop();
        transWriter.transformSampleToTarget();
        Boolean transformed = transWriter.transformMixNormal();
        System.out.println("Find Normal? " + transformed);
        System.out.println(transWriter.getCode());
        System.out.println(transWriter.getStanCode());
    }

    @Test
    public void TestNewNormal2T() throws Exception {
        TransWriter transWriter = new TransWriter("src/test/resources/stan/radon.pooling.stan",
                "src/test/resources/stan/radon.pooling.data.R");
        transWriter.transformObserveToLoop();
        transWriter.transformSampleToTarget();
        Boolean transformed = transWriter.transformNewNormal2T();
        System.out.println("Find Normal? " + transformed);
        System.out.println(transWriter.getCode());
        System.out.println(transWriter.getStanCode());
    }

    @Test
    public void TestConstToParam() throws Exception {
        TransWriter transWriter = new TransWriter("src/test/resources/stan/radon.pooling.stan",
               "src/test/resources/stan/radon.pooling.data.R");
        // TransWriter transWriter = new TransWriter("src/test/resources/poisson.template");
        // transWriter.transformObserveToLoop();
        // transWriter.transformSampleToTarget();
        // Boolean transformed = transWriter.transformConstToParam();
        // System.out.println("Transformed? " + transformed);
        // transWriter.transformLocalizer(1);
        System.out.println(transWriter.getCode());
        System.out.println(transWriter.getStanCode());

    }

    @Test
    public void TestReparamLocalizer2() throws Exception {
        TransWriter transWriter = new TransWriter("src/test/resources/poisson.template");
        transWriter.transformObserveToLoop();
        transWriter.transformSampleToTarget();
        Boolean transformed = transWriter.transformReparamLocalizer();
        System.out.println("Find Normal? " + transformed);
        System.out.println(transWriter.getCode());
        System.out.println(transWriter.getStanCode());

    }

    @Test
    public void TestTransformAll() throws Exception {
        File folder = new File("../PPVM/templates/org/");
        File[] listOfFiles = folder.listFiles();
        StanFileWriter stanFileWriter = new StanFileWriter();
        String targetOrgDir = "../PPVM/autotemp/newtrans0403/";
        //TODO: check sshfs mount; before run ./patch_vector.sh; after finish run ./patch_simplex.sh

        int i = 0;
        ArrayList<String> restFiles=new ArrayList<>();
        for (File orgProgDir : listOfFiles) {
            if (orgProgDir.isDirectory()) {
                if (!orgProgDir.getName().contains("normal_mixture"))
//                if (orgProgDir.getName().contains("normal_mix") || orgProgDir.getName().contains("M0") ) // (!orgProgDir.getName().contains("koyck") )
                    continue;
                try {
                    File newDir = new File(targetOrgDir + orgProgDir.getName());
                    newDir.mkdir();
                    System.out.println(orgProgDir.getAbsolutePath());
                    for (File orgProgDirFile : orgProgDir.listFiles())
                        if (orgProgDirFile.getName().equals(orgProgDir.getName() + ".stan")) {
                            FileUtils.copyFileToDirectory(orgProgDirFile.getAbsoluteFile(), newDir);
                        } else if (orgProgDirFile.getName().equals(orgProgDir.getName() + ".data.R")) {
                            FileUtils.copyFileToDirectory(orgProgDirFile, newDir);
                        }
                        // else if (orgProgDirFile.getName().equals("gen_data.R")) {
                    //         FileUtils.copyFileToDirectory(orgProgDirFile, newDir);
                    //     }
                    stanFileWriter.tryAllTrans(newDir);
                } catch (Exception e) {
                    restFiles.add(orgProgDir.getName());
                    e.printStackTrace();
                }
            }
            i++;
            // if (i>1)
            //     break;
        }
        System.out.println(restFiles);

    }
}
