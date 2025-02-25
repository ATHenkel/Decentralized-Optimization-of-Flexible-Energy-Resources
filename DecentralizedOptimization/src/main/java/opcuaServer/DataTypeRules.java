package opcuaServer;

import java.util.ArrayList;
import java.util.List;

public class DataTypeRules {
    static class DataTypeRule {
        String prefix;
        String suffix;
        String dataType;

        DataTypeRule(String prefix, String suffix, String dataType) {
            this.prefix = prefix;
            this.suffix = suffix;
            this.dataType = dataType;
        }
    }

    private List<DataTypeRule> rules;

    public DataTypeRules() {
    	// Initialize the list of rules when creating the object
        this.rules = new ArrayList<>();
        initializeRules();
    }

    private void initializeRules() {
    	
    	/**
    	Global Data Types
    	*/
        addRule(null, "WQC", "Byte");
        addRule(null, "VUnit", "Int");
        addRule(null, "CommandInfo", "DWord");
        //Service Interface acc. 2658-4
        addRule(null, "ConfigParamApplyEn", "Bool");
        addRule(null, "ConfigParamApplyExt", "Bool");
        addRule(null, "ConfigParamApplyInt", "Bool");
        addRule(null, "ConfigParamApplyOp", "Bool");
        addRule(null, "ProcParamApplyEn", "Bool");
        addRule(null, "ProcParamApplyExt", "Bool");
        addRule(null, "ProcParamApplyInt", "Bool");
        addRule(null, "ProcParamApplyOp", "Bool");
        addRule(null, "SrcExtAct", "Bool");
        addRule(null, "SrcIntAct", "Bool");
        addRule(null, "SrcExtOp", "Bool");
        addRule(null, "SrcIntOp", "Bool");
        addRule(null, "SrcIntAut", "Bool");
        addRule(null, "SrcExtAut", "Bool");
        addRule(null, "SrcChannel", "Bool");
        addRule(null, "StateOffAct", "Bool");
        addRule(null, "StateAutAct", "Bool");
        addRule(null, "StateOpAct", "Bool");
        addRule(null, "StateAutOp", "Bool");
        addRule(null, "StateOpOp", "Bool");
        addRule(null, "StateOffOp", "Bool");
        addRule(null, "StateAutAut", "Bool");
        addRule(null, "StateOpAut", "Bool");
        addRule(null, "StateOffAut", "Bool");
        addRule(null, "StateChannel", "Bool");
        addRule(null, "ReportValueFreeze", "Bool");
        addRule(null, "CommandEn", "DWord");
        addRule(null, "InteractAddInfo", "String");
        addRule(null, "InteractAnswerID", "DWord");
        addRule(null, "InteractQuestionID", "DWord");
        addRule(null, "PosTextID", "DWord");
        addRule(null, "ProcedureReq", "DWord");
        addRule(null, "ProcedureCur", "DWord");
        addRule(null, "StateCur", "DWord");
        addRule(null, "ProcedureExt", "DWord");
        addRule(null, "ProcedureInt", "DWord");
        addRule(null, "ProcedureOp", "DWord");
        addRule(null, "CommandExt", "DWord");
        addRule(null, "CommandInt", "DWord");
        addRule(null, "CommandOp", "DWord");
        addRule(null, "OSLevel", "Byte");
    	
        /**
    	AnaView
    	*/
        addRule("AnaView", "V", "Real");
        addRule("AnaView", "VSclMin", "Real");
        addRule("AnaView", "VSclMax", "Real");
        
        /**
    	DIntView
    	*/
        addRule("DIntView", "V", "DInt");
        addRule("DIntView", "VSclMin", "DInt");
        addRule("DIntView", "VSclMax", "DInt");
    }

    public void addRule(String prefix, String suffix, String dataType) {
        rules.add(new DataTypeRule(prefix, suffix, dataType));
    }

    public String determineDataType(String identifier) {
        int firstUnderscoreIndex = identifier.indexOf('_');
        if (firstUnderscoreIndex == -1) return null; // Kein Unterstrich gefunden
        String prefix = identifier.substring(0, firstUnderscoreIndex);
        
        // Alles nach dem ersten Unterstrich
        String remainingString = identifier.substring(firstUnderscoreIndex + 1);
        
        // Finden des letzten Punktes im verbleibenden String
        int lastDotIndex = remainingString.lastIndexOf('.');
        if (lastDotIndex == -1) {
            // Behandlung von Fällen, in denen kein Punkt vorhanden ist
            for (DataTypeRule rule : rules) {
                if ((rule.prefix == null || rule.prefix.equals(prefix)) && rule.suffix.equals(remainingString)) {
                    return rule.dataType;
                }
            }
        } else {
            String suffix = remainingString.substring(lastDotIndex + 1);
        
            for (DataTypeRule rule : rules) {
                if ((rule.prefix == null || rule.prefix.equals(prefix)) && rule.suffix.equals(suffix)) {
                    return rule.dataType;
                }
            }
        }
        
        return null; // oder Rückgabe eines Standarddatentyps
    }

}

