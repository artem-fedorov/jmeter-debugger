package com.blazemeter.jmeter.debugger.gui;


import org.apache.jmeter.exceptions.IllegalUserActionException;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jorphan.collections.HashTree;

import java.util.Map;

public class DebuggerTreeModel extends JMeterTreeModel {
    public DebuggerTreeModel(HashTree val) {
        for (Object o : val.getArray()) {
            TestElement obj = (TestElement) o;
            setRoot(new JMeterTreeNode(obj, this));
            try {

                for (Map.Entry<Object, HashTree> tg : val.entrySet()) {
                    addSubTree(tg.getValue(), (JMeterTreeNode) root);
                }

            } catch (IllegalUserActionException e) {
                throw new RuntimeException("", e);
            }
        }
    }
}