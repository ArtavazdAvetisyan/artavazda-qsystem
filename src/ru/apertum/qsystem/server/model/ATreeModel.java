/*
 *  Copyright (C) 2010 {Apertum}Projects. web: www.apertum.ru email: info@apertum.ru
 * 
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ru.apertum.qsystem.server.model;

import java.util.LinkedList;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import ru.apertum.qsystem.common.QLog;
import ru.apertum.qsystem.common.exceptions.ServerException;
import ru.apertum.qsystem.server.Spring;
import ru.apertum.qsystem.server.controller.IServerListener;
import ru.apertum.qsystem.server.controller.ServerEvents;

/**
 *
 * @param <T> 
 * @author Evgeniy Egorov
 */
public abstract class ATreeModel<T extends ITreeIdGetter> extends DefaultTreeModel {

    protected ATreeModel() {
        super(null);
        createTree();
        ServerEvents.getInstance().registerListener(new IServerListener() {

            @Override
            public void restartEvent() {
                createTree();
            }
        });
    }

    protected abstract LinkedList<T> load();

    protected final void createTree() {
        nodes = load();
        for (T node : nodes) {
            if (node.getParentId() == null) {
                setRoot(node);
                break;
            }
        }
        bildTree(getRoot());
        QLog.l().logger().info("Создали дерево.");
    }

    private void bildTree(T root) {
        for (T node : nodes) {
            if (root.getId().equals(node.getParentId())) {
                node.setParent(root);
                root.addChild(node);
                bildTree(node);
            }
        }
    }
    private LinkedList<T> nodes;

    public LinkedList<T> getNodes() {
        return nodes;
    }

    /**
     * Получить услугу по ID
     * @param id
     * @return если не найдено то вернет null.
     */
    public T getById(long id) {
        for (T node : nodes) {
            if (id == node.getId()) {
                return node;
            }
        }
        throw new ServerException("Не найдена услуга по ID \"" + id + "\"");
    }

    /**
     * Проверка наличия услуги по id
     * @param id имя проверяемой услуги
     * @return есть или нет
     */
    public boolean hasById(long id) {
        for (T node : nodes) {
            if (id == node.getId()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Проверка наличия услуги по id
     * @param name имя проверяемой услуги
     * @return есть или нет
     */
    public boolean hasByName(String name) {
        for (T node : nodes) {
            if (name.equals(node.getName())) {
                return true;
            }
        }
        return false;
    }

    public int size() {
        return nodes.size();
    }

    /**
     * Перебор всех услуг до одной включая корень и узлы
     * @param root
     * @param listener
     */
    public static void sailToStorm(TreeNode root, ISailListener listener) {
        seil(root, listener);
    }

    private static void seil(TreeNode parent, ISailListener listener) {
        listener.actionPerformed(parent);
        for (int i = 0; i < parent.getChildCount(); i++) {
            seil(parent.getChildAt(i), listener);
        }
    }

    @Override
    public T getRoot() {
        return (T) super.getRoot();
    }

    @Override
    public T getChild(Object parent, int index) {
        return (T) ((TreeNode) parent).getChildAt(index);
    }

    @Override
    public int getChildCount(Object parent) {
        return ((TreeNode) parent).getChildCount();
    }

    @Override
    public boolean isLeaf(Object node) {
        return ((TreeNode) node).isLeaf();
    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
        return ((TreeNode) parent).getIndex((TreeNode) child);
    }

    @Override
    public void removeNodeFromParent(MutableTreeNode node) {
        sailToStorm(node, new ISailListener() {

            @Override
            public void actionPerformed(TreeNode node) {
                deleted.add((T) node);
            }
        });
        deleted.add((T) node);
        nodes.removeAll(deleted);

        super.removeNodeFromParent(node);
    }
    protected final LinkedList<T> deleted = new LinkedList<>();

    @Override
    public void insertNodeInto(MutableTreeNode newChild, MutableTreeNode parent, int index) {
        nodes.add((T) newChild);
        super.insertNodeInto(newChild, parent, index);
    }

    public void save() {
        // Вложенные нужно убрать. т.к. они сотрутся по констрейнту
        final LinkedList<T> del = new LinkedList<>();
        for (T t : deleted) {
            boolean flag = false;
            T parent = (T) t.getParent();
            while (parent != null && !flag) {
                for (T t2 : deleted) {
                    if (t2.getId().equals(parent.getId())) {
                        flag = true;
                    }
                }
                if (!flag){
                    parent = (T) parent.getParent();
                }
            }
            if (flag) {
                del.add(t);
            }
        }
        deleted.removeAll(del);
        Spring.getInstance().getHt().deleteAll(deleted);
        deleted.clear();
        Spring.getInstance().getHt().saveOrUpdateAll(nodes);
    }
}
