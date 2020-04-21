package translators;

import grammar.AST;
import grammar.cfg.*;
import org.apache.commons.lang3.tuple.Pair;
import utils.Utils;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Math.round;

public class PsiMatheTranslator implements ITranslator{

    private String defaultIndent = "\t";
    private OutputStream out;
    private OutputStream Matheout;
    private Set<BasicBlock> visited;
    private StringBuilder stringBuilder;
    private StringBuilder output;
    private String pathDirString;
    private Boolean nomean=true;
    private Integer dataReduceRatio=20;
    private String transformparamOut = "";
    private String bodyString;
    private HashMap<String, AST.Decl> paramDeclStatement = new HashMap<>();
    private Set<String> paramPriorNotAdded = new HashSet<>();
    private final List<JsonObject> models= Utils.getDistributions(null);
    private HashMap<String, Integer> constMap = new HashMap<>();


    public void setOut(OutputStream o){
        out = o;
    }
    public void setMatheOut(OutputStream o){
        Matheout = o;
        dumpMathe("Get[\"/Users/zixin/Documents/uiuc/fall19/are/psense_poly/base0417.m\"];\n");
    }

    public void setPath(String s) {
        pathDirString = s;
    }

    public void parseBlock(BasicBlock block){

        ArrayList<Statement>  stmts = block.getStatements();

        if(!visited.contains(block)) {
            visited.add(block);
            StringBuilder sb = new StringBuilder();
            Statement st = null;

            for(Statement stmt : stmts){
                String res = parse(stmt);
                sb.append(res);
            }
            dump(sb.toString());

            for (Edge e : block.getEdges()) {
                parseBlock(e.getTarget());
            }
        }

    }
    private String dumpR(ArrayList<AST.Data> dataSets) {
        StringWriter stringWriter = null;
        stringWriter = new StringWriter();

        for (AST.Data data : dataSets) {
            String dataString = Utils.parseData(data, 'f');
            String dimsString = "";
            if (data.decl.dtype.dims != null && data.decl.dtype.dims.dims.size() > 0) {
                dimsString += data.decl.dtype.dims.toString();
            }
            if (data.decl.dims != null && data.decl.dims.dims.size() > 0) {
                if (dimsString.length() > 0) {
                    dimsString += ",";
                }
                dimsString += data.decl.dims.toString();
            }

            dataString = dataString.replaceAll("\\s", "").replaceAll("\\[", "").replaceAll("\\]", "").replaceAll(".0,",",").replaceAll(".0$","");
            if (dimsString.length() == 0) {
                if(dataString.contains(".")) {
                    stringWriter.write(String.format("%s := %s;\n", data.decl.id, dataString));
                } else {
                    dataString = String.valueOf((round(Integer.valueOf(dataString)/dataReduceRatio)));
                    stringWriter.write(String.format("%s := %s;\n", data.decl.id, dataString));
                    constMap.put(data.decl.id.id,(Integer.valueOf(dataString)));
                }
                // write to mathe
                if (!data.decl.id.id.equals("N"))
                    dumpMathe(data.decl.id.id + "= " + dataString + "\n");
            } else if (dimsString.split(",").length == 1) {
                // more data to file
                stringWriter.write(String.format("%1$s := readCSV(\"%1$s_data_csv\");\n", data.decl.id));
                String[] dataStringSplit = dataString.split(",");
                Integer dataLength = dataStringSplit.length;
                dataString = String.join(",",Arrays.copyOfRange(dataStringSplit,0,round(dataLength/10)));
                String addOneDataString = "1," + String.join(",",Arrays.copyOfRange(dataStringSplit,0,round(dataLength/10)));
                try {

                    FileOutputStream out = new FileOutputStream(String.format("%1$s/%2$s_data_csv",pathDirString,data.decl.id));
                    out.write(addOneDataString.getBytes());
                    out.write("\n".getBytes());
                    out.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                // write to mathe
                dumpMathe(data.decl.id.id + "= {" + dataString + "}\n");

            } else if (dimsString.split(",").length == 2) {
                String[] splited = dimsString.split(",");
                String[] dataSplited = dataString.split(",");
                List<String> dataS = new ArrayList<>();
                int outterDim= Integer.valueOf(splited[0]);
                for(int i = 0; i < outterDim; ++i){
                    StringBuilder res = new StringBuilder();
                    int innerDim = Integer.valueOf(splited[1]);
                    res.append("[");
                    for(int j = 0; j < innerDim; ++j){
                        res.append(dataSplited[i+j*outterDim]);
                        if(j != innerDim-1){
                            res.append(",");
                        }
                    }
                    res.append("]");
                    dataS.add(res.toString());
                }
                stringWriter.write(String.format("%s := [%s];\n", data.decl.id,  String.join(",", dataS)));
            } else {

            }

        }
        try {
            stringWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return stringWriter.toString();
    }




    private String translate_block(BasicBlock bBlock) {
        String output = "";
        if (bBlock.getStatements().size() == 0)
            return output;

        for (Statement statement : bBlock.getStatements()) {
            if (statement.statement instanceof AST.AssignmentStatement) {
                AST.AssignmentStatement assignmentStatement = (AST.AssignmentStatement) statement.statement;
                String tempRhs = assignmentStatement.rhs.toString();
                String newRhs;
                AST.Decl lhsDecl = paramDeclStatement.get(assignmentStatement.lhs.toString());
                if (lhsDecl != null && lhsDecl.annotations.size() > 0) {
                    paramPriorNotAdded.remove(lhsDecl.id.toString());
                    String dist = tempRhs.split("\\(")[0];
                    String params = tempRhs.replace(dist,"").substring(1,tempRhs.length() - dist.length() - 1);
                    String innerParams = "";
                    for (JsonObject model : this.models) {
                        if (dist.equals(model.getString("name"))) {
                            JsonArray modelParams = model.getJsonArray("args");
                            for (JsonValue iipp : modelParams) {
                                String paramName = iipp.asJsonObject().getString("name");
                                innerParams += "," + paramName;
                            }
                        }
                    }

                    newRhs = String.format("sampleFrom(\"(x;%2$s) => PDF[%1$sDistribution[%2$s],x]\", %3$s)",
                            dist.substring(0,1).toUpperCase() + dist.substring(1),
                            innerParams.substring(1),
                            params
                            );
                }
                else{
                    newRhs = tempRhs;
                    System.out.println("===================");
                    System.out.println(assignmentStatement.lhs);
                    System.out.println(newRhs);
                }
                String assignStr;
                if (lhsDecl != null && (lhsDecl.dims != null || lhsDecl.dtype.dims != null)) {
                    String loopDim;
                    if (lhsDecl.dims != null) {
                        loopDim = lhsDecl.dims.toString();
                    }
                    else {
                        loopDim = lhsDecl.dtype.dims.toString();
                    }
                    assignStr = String.format("for ppjj in [1..%1$s+1) {\n",loopDim);
                    assignStr += String.format("%1$s[ppjj] = %2$s;\n",assignmentStatement.lhs,newRhs);
                    assignStr += "}\n";

                } else {
                    // Deal with target +=
                    if (assignmentStatement.lhs.toString().equals("target")) {
                        String dist = tempRhs.split("_lpdf\\(")[0].split("target\\+")[1];
                        String[] paramsList = tempRhs.split("_lpdf\\(")[1].split(",");
                        String firstParam = paramsList[0];
                        String params = tempRhs.split("_lpdf\\(")[1].replace(")","").replace(firstParam+",","");
                        String innerParams = "";
                        for (JsonObject model : this.models) {
                            if (dist.equals(model.getString("name"))) {
                                JsonArray modelParams = model.getJsonArray("args");
                                for (JsonValue iipp : modelParams) {
                                    String paramName = iipp.asJsonObject().getString("name");
                                    innerParams += "," + paramName;
                                }
                            }
                        }
                        newRhs = String.format("cobserve(sampleFrom(\"(x;%2$s,i) => PDF[%1$sDistribution[%2$s],x+Delta[i]]\", %3$s,observe_i),%4$s)",
                                dist.substring(0,1).toUpperCase() + dist.substring(1),
                                innerParams.substring(1),
                                params,
                                firstParam //.replace("observe_i","observe_i-1")

                        );
                        assignStr = newRhs;
                        assignStr += ";\n";
                        // write observe alternative for transformations
                        // original
                        try {
                            FileOutputStream out = new FileOutputStream(String.format("%1$s/OrgObserve",pathDirString));
                            out.write(newRhs.getBytes());
                            out.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        // reparam
                        if (dist.equals("normal")) {
                            try {
                                String newRhsTrans;
                                newRhsTrans = String.format(
                                        "cobserve(sampleFrom(\"(x;mu,sigma,i) => PDF[GammaDistribution[1/2,1/2],weight[i]]*PDF[NormalDistribution[mu,sigma*weight[i]^(-1/2)],x+Delta[i]]\", %1$s,observe_i),%2$s)",
                                        params,
                                        firstParam);
                                FileOutputStream out = new FileOutputStream(String.format("%1$s/ReparamTransObserve", pathDirString));
                                out.write(newRhsTrans.getBytes());
                                out.close();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            try {
                                String newRhsTrans;
                                newRhsTrans = String.format(
                                        "cobserve(sampleFrom(\"(x;mu,sigma,i) => PDF[StudentTDistribution[mu,sigma,weight],x+Delta[i]]\", %1$s,observe_i),%2$s)",
                                        params,
                                        firstParam);
                                FileOutputStream out = new FileOutputStream(String.format("%1$s/StudentTransObserve", pathDirString));
                                out.write(newRhsTrans.getBytes());
                                out.close();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            // Mixture
                            if ( ! assignmentStatement.rhs.toString().contains("log_mix"))
                                try {
                                    String newRhsTrans;
                                    newRhsTrans = String.format(
                                            "cobserve(sampleFrom(\"(x;mu,nu,i) => (1-weight0)*PDF[NormalDistribution[mu,nu],x+Delta[i]]+weight0*PDF[NormalDistribution[mu,weight1],x+Delta[i]]\", %1$s,observe_i),%2$s)",
                                            params,
                                            firstParam);
                                    FileOutputStream out = new FileOutputStream(String.format("%1$s/MixtureTransObserve", pathDirString));
                                    out.write(newRhsTrans.getBytes());
                                    out.close();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                        }
                        // Reweight
                        try {
                            String newRhsTrans;
                            newRhsTrans = String.format("cobserve(sampleFrom(\"(x;%2$s,i) => PDF[%1$sDistribution[%2$s],x+Delta[i]]^(weight[i])\", %3$s,observe_i),%4$s)",
                                    dist.substring(0,1).toUpperCase() + dist.substring(1),
                                    innerParams.substring(1),
                                    params,
                                    firstParam
                            );
                            FileOutputStream out = new FileOutputStream(String.format("%1$s/ReweightTransObserve", pathDirString));
                            out.write(newRhsTrans.getBytes());
                            out.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        // Local1
                        try {
                            String newRhsTrans;
                            newRhsTrans = String.format("cobserve(sampleFrom(\"(x;%2$s,i) => PDF[NormalDistribution[0,0.25],weight[i]]*PDF[%1$sDistribution[%5$s],x+Delta[i]]\", %3$s,observe_i),%4$s)",
                                    dist.substring(0,1).toUpperCase() + dist.substring(1),
                                    innerParams.substring(1),
                                    params,
                                    firstParam,
                                    innerParams.substring(1).replaceFirst(",","+(weight[i]),")
                            );
                            FileOutputStream out = new FileOutputStream(String.format("%1$s/Local1TransObserve", pathDirString));
                            out.write(newRhsTrans.getBytes());
                            out.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        // Local2
                        if (innerParams.substring(1).contains(",")) {
                            try {
                                String newRhsTrans;
                                String[] innerParamsList = innerParams.substring(1).split(",");
                                innerParamsList[1] = innerParamsList[1] + "+(weight[i])";
                                newRhsTrans = String.format("cobserve(sampleFrom(\"(x;%2$s,i) => PDF[NormalDistribution[0,0.25],weight[i]]*PDF[%1$sDistribution[%5$s],x+Delta[i]]\", %3$s,observe_i),%4$s)",
                                        dist.substring(0, 1).toUpperCase() + dist.substring(1),
                                        innerParams.substring(1),
                                        params,
                                        firstParam,
                                        String.join(",", innerParamsList)
                                );
                                FileOutputStream out = new FileOutputStream(String.format("%1$s/Local2TransObserve", pathDirString));
                                out.write(newRhsTrans.getBytes());
                                out.close();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    } // end dealing with target
                    else {
                        assignStr = assignmentStatement.lhs + " = " + newRhs + ";\n";
                    }
                }
                // for (AST.Annotation ann : statement.statement.annotations){
                //     if(ann.annotationType == AST.AnnotationType.Observe){
                //         assignStr = "observe(" + assignStr + ")";
                //     }
                // }
                output += assignStr;
                System.out.println(paramPriorNotAdded);
                if (paramPriorNotAdded.isEmpty()) {
                    output += transformparamOut;
                    // transformed param calc in mathe
                    String[] allLines = transformparamOut.replace("_","MMMM").split("\n");
                    for (String ll:allLines) {
                        if (ll.contains(":=")) {
                            if (ll.contains("array")) {
                                String[] lls = ll.split("(array\\(|\\+1,|\\))");
                                if (constMap.containsKey(lls[1]))
                                    lls[1] = constMap.get(lls[1]).toString();
                                dumpMathe(lls[0] + String.format("Table[%1$s, {i,1,%2$s}]", lls[2], lls[1]) + "\n");

                            } else {
                                dumpMathe(ll + "\n");
                            }

                        }
                        else if (ll.contains("for")) {
                            String[] lls = ll.split("(for| in |\\[|\\.\\.|\\+1\\))");
                            dumpMathe(String.format("For[%1$s=%2$s,%1$s<=%3$s,%1$s++,\n",lls[1],lls[3],lls[4]));
                        }
                        else {
                            if (ll.contains("[")){
                                String newll = ll;
                                List<String> allMatches = new ArrayList<String>();
                                Matcher m = Pattern.compile("\\w+\\[").matcher(ll);
                                while (m.find()) {
                                    allMatches.add(m.group());
                                }
                                for (String mm:allMatches) {
                                    String paramKey = mm.replace("MMMM","_").replace("[","");
                                    if (paramDeclStatement.containsKey(paramKey) ){
                                        AST.Decl paramDecl = paramDeclStatement.get(paramKey);
                                        if (paramDecl.annotations.size() >0 &&
                                            (paramDecl.annotations.get(0).annotationType.toString().equals("Prior") ||
                                                    paramDecl.annotations.get(0).annotationType.toString().equals("Limits")
                                            )
                                            ) // is a param
                                        newll = newll.replace(mm,"addrParam[" + mm.replace("[","") + ",");
                                    }

                                }
                                // not always i
                                newll = newll.replaceAll("\\[(\\w+)]","[[$1]]");
                                dumpMathe(newll.replace("{","").replace("}","]"));
                            } else if (! ll.matches("\\s+"))
                                dumpMathe(ll.replace("{","").replace("}","]"));
                        }

                    }
                    transformparamOut = "";
                }
            } else if (statement.statement instanceof AST.ForLoop) {
                AST.ForLoop loop = (AST.ForLoop) statement.statement;
                output += "for " + loop.loopVar + " in [" + loop.range.start + ".." + loop.range.end + "+1) \n";
            } else if (statement.statement instanceof AST.Decl) {
                AST.Decl declaration = (AST.Decl) statement.statement;
                paramDeclStatement.put(declaration.id.toString(),declaration);
                if (declaration.annotations.size() > 0 &&
                        (declaration.annotations.get(0).annotationType.toString().equals("Prior") ||
                                declaration.annotations.get(0).annotationType.toString().equals("Limits")
                        )){
                    paramPriorNotAdded.add(declaration.id.toString());
                    if (declaration.dtype.dims != null || declaration.dims != null) {
                        String loopDim;
                        if (declaration.dtype.dims != null)
                            loopDim = declaration.dtype.dims.toString();
                        else
                            loopDim = declaration.dims.toString();
                        output += String.format(" %1$s := array(%2$s+1);\n", declaration.id, loopDim);
                        if (! (bodyString.contains(declaration.id + "=normal") ||bodyString.contains(declaration.id + "=gamma") || bodyString.contains(declaration.id + "=inv_gamma") )) {
                            paramPriorNotAdded.remove(declaration.id.toString());
                            output += String.format("for ppjj in [1..%1$s+1) {\n",loopDim);
                            if (declaration.annotations.size() > 1) {
                                for(AST.Annotation currAnno : declaration.annotations){
                                    if(currAnno.annotationType.toString().equals("Limits")){
                                        String lower = currAnno.annotationValue.toString().split("(<lower=|,|>)")[1];
                                        if (lower.matches("[0-9]+"))
                                            output += declaration.id + "[ppjj] = sampleFrom(\"(c) => [c>" + lower + "]\");\n";
                                    }
                                }
                            } else {
                                output += declaration.id + "[ppjj] = sampleFrom(\"(c) => [c=c]\");\n";
                            }
                            output += "}\n";

                        }
                    } else {
                        if (bodyString.contains(declaration.id + "=normal") ||bodyString.contains(declaration.id + "=gamma") || bodyString.contains(declaration.id + "=inv_gamma") ) {
                            output += declaration.id + " := 0;\n";
                        } else {
                            paramPriorNotAdded.remove(declaration.id.toString());
                            if (declaration.annotations.size() > 1) {
                                for(AST.Annotation currAnno : declaration.annotations){
                                    if(currAnno.annotationType.toString().equals("Limits")){
                                        String lower = currAnno.annotationValue.toString().split("(<lower=|,|>)")[1];
                                        if (lower.matches("[0-9]+"))
                                            output += declaration.id + " := sampleFrom(\"(c) => [c>" + lower + "]\");\n";
                                    }
                                }
                            } else {
                                output += declaration.id + " := sampleFrom(\"(c) => [c=c]\");\n";
                            }
                        }

                    }
                }
                else {
                    if (declaration.dtype.dims != null) {
                        output += String.format(" %1$s := array(%2$s+1,1);\n",declaration.id,declaration.dtype.dims);
                    } else if (declaration.dims != null){
                        output += String.format(" %1$s := array(%2$s+1,1);\n",declaration.id,declaration.dims);
                    } else {
                        output += declaration.id + " := 0;\n";

                    }
                }
            }
            else if(statement.statement instanceof AST.IfStmt){
                AST.IfStmt ifStmt = (AST.IfStmt) statement.statement;
                output += "if(" + ifStmt.condition.toString() + ")\n";
            }
        }
        return output;
    }
    @Override
    public void translate(ArrayList<Section> sections) throws Exception {
        stringBuilder = new StringBuilder();
        visited = new HashSet<>();
        for (Section section : sections){
            if(section.sectionType == SectionType.DATA){
                if(nomean) {
                    dump("def main() {\n", "");
                    nomean = false;
                }
                dump(dumpR(section.basicBlocks.get(0).getData()));
            } else if(section.sectionType == SectionType.FUNCTION){

                if(section.sectionName == "main") {
                    if(nomean) {
                        dump("def main() {\n", "");
                        nomean = false;
                    }
                }
                if (section.sectionName.equals("main")) {
                    bodyString = section.basicBlocks.toString().replaceAll("\\s+","");
                    for (BasicBlock basicBlock : section.basicBlocks) {

                        BasicBlock curBlock = basicBlock;
                        while (!visited.contains(curBlock)) {
                            visited.add(curBlock);
                            String block_text = translate_block(curBlock);
                            if (curBlock.getIncomingEdges().containsKey("true")) {
                                block_text = "{\n" + block_text;
                            }
                            if (curBlock.getOutgoingEdges().containsKey("back")) {
                                block_text = block_text + "}\n";
                            }
                            if (curBlock.getParent().sectionName.equals("transformedparam")) {
                                transformparamOut += block_text;
                            }
                            else
                                stringBuilder.append(block_text);
                            if (curBlock.getEdges().size() > 0) {
                                BasicBlock prevBlock = curBlock;
                                if (curBlock.getEdges().size() == 1) {
                                    curBlock = curBlock.getEdges().get(0).getTarget();
                                } else {
                                    String label = curBlock.getEdges().get(0).getLabel();
                                    if (label != null && label.equalsIgnoreCase("true") && !visited.contains(curBlock.getEdges().get(0).getTarget())) {
                                        curBlock = curBlock.getEdges().get(0).getTarget();
                                    } else {
                                        curBlock = curBlock.getEdges().get(1).getTarget();
                                    }
                                }
                                if (!visited.contains(curBlock) && curBlock.getIncomingEdges().containsKey("meet") && !isIfNode(prevBlock)) {
                                    if (curBlock.getParent().sectionName.equals("transformedparam"))
                                        transformparamOut += ("}\n");
                                    else
                                        stringBuilder.append("}\n");
                                }
                            }
                        }
                    }
                }
                dump(stringBuilder.toString());

            } else if (section.sectionType == SectionType.QUERIES){
                parseQueries(section.basicBlocks.get(0).getQueries(), "");

                dump("\n}\n");
                return;
            } else {
                System.out.println("Unsupport section (ignored): " + section.sectionName + " " + section.sectionType);
                BasicBlock currBlock = section.basicBlocks.get(0);
                if (section.sectionName.equals("transformedparam")) {
                    transformparamOut = translate_block(currBlock);
                }
                else {
                    dump(translate_block(currBlock));
                }
            }
        }

    }
    private boolean isIfNode(BasicBlock basicBlock){
        return basicBlock.getStatements().size() == 1 && basicBlock.getStatements().get(0).statement instanceof AST.IfStmt;
    }
    @Override
    public Pair run() {
        return null;
    }

    public Pair run(String codeFileName){
        Pair results = Utils.runPsi(codeFileName);
        return results;
    }




    public void parseQueries(List<AST.Query> queries, String indent){
        StringBuilder retStr = new StringBuilder();
        for (AST.Decl param_i:paramDeclStatement.values()){
            if (param_i.annotations.size() > 0 &&
                    (param_i.annotations.get(0).annotationType.toString().equals("Prior") ||
                            param_i.annotations.get(0).annotationType.toString().equals("Limits")
                    )) {
                retStr.append(",");
                if (param_i.dims == null && param_i.dtype.dims == null) {
                    retStr.append(param_i.id.toString());
                } else {
                    Integer paramDims;
                    if (param_i.dims != null) {
                        if (param_i.dims.toString().matches("[1-9]+"))
                            paramDims = Integer.valueOf(param_i.dims.toString());
                        else {
                            paramDims = constMap.get(param_i.dims.toString());
                        }
                    } else {
                        if (param_i.dtype.dims.toString().matches("[1-9]+"))
                            paramDims = Integer.valueOf(param_i.dtype.dims.toString());
                        else
                            paramDims = constMap.get(param_i.dtype.dims.toString());
                    }
                    for (Integer ii = 1; ii <= paramDims; ii++) {
                        if (ii != 1)
                            retStr.append(",");
                        retStr.append(param_i.id.id + "[" + String.valueOf(ii) + "]");
                    }

                }
            }
        }
        dump("return (" + retStr.substring(1) + ");");
    }


    public void dump(String str){
        try {
           out.write(str.getBytes());
        } catch (Exception e){
            e.printStackTrace();
        }

    }
    public void dumpMathe(String str){
        try {
            Matheout.write(str.getBytes());
        } catch (Exception e){
            e.printStackTrace();
        }

    }
    public void dump(String str, String indent){
        dump(str + indent);
    }

    public String parse(Statement s){
        return parse(s.statement);
    }

    public boolean observe(AST.Statement s, StringBuilder sb){
        boolean res = true;

        for (AST.Annotation ann : s.annotations){
            if(ann.annotationType == AST.AnnotationType.Observe){
                sb.append(String.format("observe(%s)\n", s.toString()));
            } else {
                res = false;
            }
        }
        return res;
    }
    public String parse(AST.Statement s){
        StringBuilder sb = new StringBuilder();
        if(s.annotations.size()!=0){
            if (observe(s, sb)){
                return sb.toString();
            }
        }
        if (s instanceof AST.IfStmt){
            AST.IfStmt ifstmt = (AST.IfStmt) s;
            sb.append(String.format("if (%s)", ifstmt.condition));
        } else if (s instanceof AST.AssignmentStatement) {
            AST.AssignmentStatement assign = (AST.AssignmentStatement) s;
            sb.append(assign.toString() + ";\n");
        } else if (s instanceof AST.ForLoop) {
            AST.ForLoop fl = (AST.ForLoop) s;
            sb.append(String.format("for %s in [%s .. %s) ", fl.loopVar.toString(), fl.range.start, fl.range.end));
        } else if (s instanceof AST.Decl){
            AST.Decl decl = (AST.Decl) s;
            if(decl.dtype.primitive == AST.Primitive.FLOAT){
                sb.append(decl.id.toString() + " := 1.0;\n");
            } else {
                sb.append(decl.id.toString() + " := 0;\n");
            }

        } else {
            System.out.println("not covering: " + s);
        }
        return sb.toString();


    }


}
