// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package ru.sber.qa.llmdemo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;
import ru.sber.qa.llmdemo.dto.Test;
import ru.sber.qa.llmdemo.index.TmsTest;
import ru.sber.qa.llmdemo.utils.ResourcesUtils;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;


final class TmsToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        final TmsTest[] testList = getTests();

        DefaultMutableTreeNode top = new DefaultMutableTreeNode("Tests");
        createTreeNodeTests(top, testList);

        JBPanelWithEmptyText panel = new JBPanelWithEmptyText();
       // JPanel panel = new JPanel();
        Tree tree = new Tree(top);
        tree.getSelectionModel().setSelectionMode
                (TreeSelectionModel.SINGLE_TREE_SELECTION);

        
        panel.add(tree);
        MouseListener mouseListener = new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                        if (node.getUserObject() instanceof TmsTest test) {
                            // Добавляем Editor в Editor tabs
                            VirtualFile wrapper = new TmsTestVirtualFile(test);

                            FileEditorManager.getInstance(project).openFile(wrapper, true);

                        }
                    }
                }
            }
        };
        tree.addMouseListener(mouseListener);


        Content content = ContentFactory.getInstance().createContent(tree, "", false);
        toolWindow.getContentManager().addContent(content);
    }



    private void createTreeNodeTests(DefaultMutableTreeNode top, TmsTest[] tests) {
        //https://docs.oracle.com/javase/tutorial/uiswing/components/tree.html
        DefaultMutableTreeNode folder;
        DefaultMutableTreeNode nodeTest;

        for (TmsTest test : tests) {
            folder = new DefaultMutableTreeNode(test.getTest().getFolder());
            top.add(folder);
            nodeTest = new DefaultMutableTreeNode(test);
            folder.add(nodeTest);

        }


    }

    /**
     * examples tests
     * @return
     */
    private TmsTest[] getTests() {
        ObjectMapper om = new ObjectMapper();
        Test[] testList;
        try {
            testList = om.readValue(ResourcesUtils.getResources("examples/tests.json"), Test[].class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        TmsTest[] tmsTests = new TmsTest[testList.length];
        for (int i = 0; i < testList.length; i++) {
            if (testList[i] != null) {
                tmsTests[i] = new TmsTest(testList[i].getKey());
                tmsTests[i].setTest(testList[i]);
            }
        }

        return tmsTests;
    }

}
