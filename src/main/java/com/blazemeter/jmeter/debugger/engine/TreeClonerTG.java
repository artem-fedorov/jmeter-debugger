package com.blazemeter.jmeter.debugger.engine;

import com.blazemeter.jmeter.debugger.elements.*;
import org.apache.jmeter.assertions.Assertion;
import org.apache.jmeter.control.*;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.processor.PostProcessor;
import org.apache.jmeter.processor.PreProcessor;
import org.apache.jmeter.samplers.SampleListener;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.property.JMeterProperty;
import org.apache.jmeter.testelement.property.NullProperty;
import org.apache.jmeter.testelement.property.PropertyIterator;
import org.apache.jmeter.threads.AbstractThreadGroup;
import org.apache.jmeter.timers.Timer;
import org.apache.jorphan.collections.HashTree;
import org.apache.jorphan.collections.HashTreeTraverser;
import org.apache.jorphan.collections.ListedHashTree;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.LinkedList;

public class TreeClonerTG implements HashTreeTraverser {
    private static final Logger log = LoggerFactory.getLogger(TreeClonerTG.class);
    private AbstractThreadGroup onlyTG;

    private final ListedHashTree newTree = new ListedHashTree();
    private final LinkedList<Object> stack = new LinkedList<>();
    private boolean ignoring = false;

    public TreeClonerTG(AbstractThreadGroup tg) {
        this.onlyTG = tg;
    }

    @Override
    public final void addNode(Object node, HashTree subTree) {
        if (!ignoring && isIgnored(node)) {
            ignoring = true;
        }

        if (!ignoring) {
            node = addNodeToTree(node);
        }
        stack.addLast(node);
    }

    private boolean isIgnored(Object node) {
        if (node instanceof JMeterTreeNode) {
            Object te = ((JMeterTreeNode) node).getUserObject();
            return te instanceof AbstractThreadGroup && te != onlyTG;
        }
        return false;
    }

    protected Object addNodeToTree(Object node) {
        if (node instanceof JMeterTreeNode) {
            node = getClonedNode((JMeterTreeNode) node);
            newTree.add(stack, node);
        } else {
            throw new IllegalArgumentException();
        }
        return node;
    }

    private JMeterTreeNode getClonedNode(JMeterTreeNode node) {
        TestElement orig = getOriginalObject(node);
        TestElement cloned = (TestElement) orig.clone();
        TestElement altered = getAlteredElement(cloned);

        if (altered instanceof Wrapper) {
            Wrapper wrp = (Wrapper) altered;
            //noinspection unchecked
            wrp.setWrappedElement(cloned);
            PropertyIterator iter = cloned.propertyIterator();
            while (iter.hasNext()) {
                JMeterProperty prop = iter.next();
                if (!prop.getName().startsWith("TestElement")) {
                    wrp.setProperty(prop.clone());
                }
            }
        }

        if (altered instanceof OriginalLink) {
            OriginalLink link = (OriginalLink) altered;
            //noinspection unchecked
            link.setOriginal(orig);
        } else {
            log.debug("Not linking original: " + altered);
        }

        JMeterTreeNode res = new JMeterTreeNode();
        altered.setName(cloned.getName());
        altered.setEnabled(cloned.isEnabled());
        if (altered.getProperty(TestElement.GUI_CLASS) instanceof NullProperty) {
            altered.setProperty(TestElement.GUI_CLASS, ControllerDebugGui.class.getCanonicalName());
        }
        res.setUserObject(altered);
        return res;
    }

    private TestElement getAlteredElement(TestElement cloned) {
        boolean isWrappable = !(cloned instanceof TransactionController) && !(cloned instanceof TestFragmentController) && !(cloned instanceof ReplaceableController);

        TestElement userObject = cloned;
        if (!isWrappable) {
            log.debug("Forcing unwrapped: " + cloned);
        } else if (cloned instanceof AbstractThreadGroup) {
            userObject = new DebuggingThreadGroup();
            userObject.setProperty(TestElement.GUI_CLASS, DebuggingThreadGroupGui.class.getCanonicalName());
        } else if (cloned instanceof Controller) {
            userObject = getController(cloned);
        } else if (cloned instanceof PreProcessor) {
            userObject = new PreProcessorDebug();
        } else if (cloned instanceof Timer) {
            userObject = new TimerDebug();
        } else if (cloned instanceof Sampler) {
            userObject = new SamplerDebug();
        } else if (cloned instanceof PostProcessor) {
            userObject = new PostProcessorDebug();
        } else if (cloned instanceof Assertion) {
            userObject = new AssertionDebug();
        } else if (cloned instanceof SampleListener) {
            userObject = new SampleListenerDebug();
        } else {
            log.debug("Keeping element unwrapped: " + cloned);
        }
        return userObject;
    }

    private TestElement getOriginalObject(JMeterTreeNode node) {
        Object obj = (node).getUserObject();
        if (obj instanceof OriginalLink) {
            Object original = ((OriginalLink) obj).getOriginal();
            return (TestElement) original;
        } else {
            return (TestElement) obj;
        }
    }

    private TestElement getController(TestElement cloned) {
        if (cloned instanceof GenericController) {
            if (cloned instanceof ReplaceableController) {     // TODO: solve replaceable problem
                log.warn("Not supported!: " + cloned);
                return new ReplaceableGenericControllerDebug();
            } else {
                return new GenericControllerDebug();
            }
        } else {
            if (cloned instanceof ReplaceableController) {
                log.warn("Controller+Replaceable is unsupported: " + cloned);
            }
            return new ControllerDebug();
        }
    }

    @Override
    public void subtractNode() {
        if (isIgnored(stack.getLast())) {
            ignoring = false;
        }
        stack.removeLast();
    }

    @Override
    public void processPath() {
    }

    public HashTree getClonedTree() {
        return newTree;
    }
}
