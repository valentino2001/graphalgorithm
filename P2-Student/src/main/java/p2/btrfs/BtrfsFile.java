package p2.btrfs;

import p2.storage.EmptyStorageView;
import p2.storage.Interval;
import p2.storage.Storage;
import p2.storage.StorageView;

import javax.swing.*;
import java.util.List;

/**
 * A file in a Btrfs file system. it uses a B-tree to store the intervals that hold the file's data.
 */
public class BtrfsFile {

    /**
     * The storage in which the file is stored.
     */
    private final Storage storage;

    /**
     * The name of the file.
     */
    private final String name;

    /**
     * The degree of the B-tree.
     */
    private final int degree;

    private final int maxKeys;

    /**
     * The root node of the B-tree.
     */
    private BtrfsNode root;

    /**
     * The total size of the file.
     */
    private int size;

    /**
     * Creates a new {@link BtrfsFile} instance.
     *
     * @param name the name of the file.
     * @param storage the storage in which the file is stored.
     * @param degree the degree of the B-tree.
     */
    public BtrfsFile(String name, Storage storage, int degree) {
        this.name = name;
        this.storage = storage;
        this.degree = degree;
        maxKeys = 2 * degree - 1;
        root = new BtrfsNode(degree);
    }

    /**
     * Reads all data from the file.
     *
     * @return a {@link StorageView} containing all data that is stored in this file.
     */
    public StorageView readAll() {
        return readAll(root);
    }

    /**
     * Reads all data from the given node.
     *
     * @param node the node to read from.
     * @return a {@link StorageView} containing all data that is stored in this file.
     */
    private StorageView readAll(BtrfsNode node) {

        StorageView view = new EmptyStorageView(storage);

        for (int i = 0; i < node.size; i++) {
            // before i-th key and i-th child.

            // read from i-th child if it exists
            if (node.children[i] != null) {
                view = view.plus(readAll(node.children[i]));
            }

            Interval key = node.keys[i];

            // read from i-th key
            view = view.plus(storage.createView(new Interval(key.start(), key.length())));
        }

        // read from last child if it exists
        if (node.children[node.size] != null) {
            view = view.plus(readAll(node.children[node.size]));
        }

        return view;
    }

    /**
     * Reads the given amount of data from the file starting at the given start position.
     *
     * @param start the start position.
     * @param length the amount of data to read.
     * @return a {@link StorageView} containing the data that was read.
     */
    public StorageView read(int start, int length) {
        return read(start, length, root, 0, 0);
    }

    /**
     * Reads the given amount of data from the given node starting at the given start position.
     *
     * @param start the start position.
     * @param length the amount of data to read.
     * @param node the current node to read from.
     * @param cumulativeLength the cumulative length of the intervals that have been visited so far.
     * @param lengthRead the amount of data that has been read so far.
     * @return a {@link StorageView} containing the data that was read.
     */
    private StorageView read(int start, int length, BtrfsNode node, int cumulativeLength, int lengthRead) {
        //bestimmtest intervall auslesen, nach indices schauen
        //emptystorage erstellt um das eingelesene da rein zu schreiben
        StorageView view = new EmptyStorageView(storage);
        //iterieren über children, length==lengthread ist die abbruchbedingung,bis ganzes intervall eingelesen ist
        for(int i = 0; i < node.children.length || length==lengthRead; i++){
            //überprüfen ob das intervall im kindknoten liegt, bzw das gesuchte intervall(start der erste den wir einlesen wollen)
            //cumleng ist alles wo man schon drüber itertiert hat
            //case : intervall ist nicht im iten kindknoten drin
            if(start >= node.childLengths[i]+cumulativeLength &&node.children[i]!=null){
                //cumlength wird erhöht wenn man über ein knoten geht
                cumulativeLength+=node.childLengths[i];
                //ganzes/teil intervall liegt im kindknoten
            }else if (node.children[i]!=null){
                //in storage hinzufügen: rufen read mit dem kind rekursiv auf
                view=view.plus(read(start, length, node.children[i], cumulativeLength, lengthRead));
            }
            //überprüfen: ob start im iten key liegt
            if(cumulativeLength + node.keys[i].length() >start){
                //int initialisieren, für länge vom auslesen
                int intervall;
                //case: intervall länger als key.length
                if(start+length>=cumulativeLength+node.keys[i].length()){
                    //kindknoten muss besucht werden, weil intervall zu lang (intervall länger als key)
                    //lesen vom start bis zum ende des keys
                    intervall= start+length-cumulativeLength-node.keys[i].length();
                }else{
                    // intervall passt komplett in key.len, lesen von start bis length
                    intervall = start+length-cumulativeLength;
                }
                //intervall einlesen, nachdem festgelegt wie lang intervall ist
                view = view.plus(storage.createView(new Interval(start-cumulativeLength,intervall)));
                //intervall auf cumlength addieren
                cumulativeLength+=node.keys[i].length();
                //wie viel wir noch einlesen müssen, falls noch nicht fertig
                lengthRead+=cumulativeLength-start;
            }
        }
        //intervall welches wir ausgelesen haben returnen
        return view;
    }

    /**
     * Insert the given data into the file starting at the given start position.
     *
     * @param start the start position.
     * @param intervals the intervals to write to.
     * @param data the data to write into the storage.
     */
    public void insert(int start, List<Interval> intervals, byte[] data) {

        // fill the intervals with the data
        int dataPos = 0;
        for (Interval interval : intervals) {
            storage.write(interval.start(), data, dataPos, interval.length());
            dataPos += interval.length();
        }

        size += data.length;

        int insertionSize = data.length;

        // findInsertionPosition assumes that the current node is not full
        if (root.isFull()) {
            split(new IndexedNodeLinkedList(null, root, 0));
        }

        insert(intervals, findInsertionPosition(new IndexedNodeLinkedList(
            null, root, 0), start, 0, insertionSize, null), insertionSize);

    }

    /**
     * Inserts the given data into the given leaf at the given index.
     *
     * @param intervals the intervals to insert.
     * @param indexedLeaf The node and index to insert at.
     * @param remainingLength the remaining length of the data to insert.
     */
    private void insert(List<Interval> intervals, IndexedNodeLinkedList indexedLeaf, int remainingLength) {

        throw new UnsupportedOperationException("Not implemented yet"); //TODO H2 c): remove if implemented
    }

    /**
     * Finds the leaf node and index at which new intervals should be inserted given a start position.
     * It ensures that the start position is not in the middle of an existing interval
     * and updates the childLengths of the visited nodes.
     *
     * @param indexedNode The current Position in the tree.
     * @param start The start position of the intervals to insert.
     * @param cumulativeLength The length of the intervals in the tree up to the current node and index.
     * @param insertionSize The total size of the intervals to insert.
     * @param splitKey The right half of the interval that had to be split to ensure that the start position
     *                 is not in the middle of an interval. It will be inserted once the leaf node is reached.
     *                 If no split was necessary, this is null.
     * @return The leaf node and index, as well as the path to it, at which the intervals should be inserted.
     */
    private IndexedNodeLinkedList findInsertionPosition(IndexedNodeLinkedList indexedNode,
                                                        int start,
                                                        int cumulativeLength,
                                                        int insertionSize,
                                                        Interval splitKey) {

        throw new UnsupportedOperationException("Not implemented yet"); //TODO H2 b): remove if implemented
    }

    /**
     * Splits the given node referenced via the {@linkplain IndexedNodeLinkedList indexedNode} parameter in the middle. <br>
     * The method ensures that the given indexedNode points to correct {@linkplain IndexedNodeLinkedList#node node} and {@linkplain IndexedNodeLinkedList#index index} after the split.
     *
     * @param indexedNode The path to the node to split, represented by a {@link IndexedNodeLinkedList}
     *
     * @see IndexedNodeLinkedList
     */
    private void split(IndexedNodeLinkedList indexedNode) {

    }

    /**
     * Writes the given data to the given intervals and stores them in the file starting at the given start position.
     * This method will override existing data starting at the given start position.
     *
     * @param start the start position.
     * @param intervals the intervals to write to.
     * @param data the data to write into the storage.
     */
    public void write(int start, List<Interval> intervals, byte[] data) {
        throw new UnsupportedOperationException("Not implemented yet"); //TODO H4 a): remove if implemented
    }

    /**
     * Removes the given number of bytes starting at the given position from this file.
     *
     * @param start the start position of the bytes to remove
     * @param length the amount of bytes to remove
     */
    public void remove(int start, int length) {
        size -= length;
        int removed = remove(start, length, new IndexedNodeLinkedList(null, root, 0), 0, 0);

        // check if we have traversed the whole tree
        if (removed < length) {
            throw new IllegalArgumentException("start + length is out of bounds");
        } else if (removed > length) {
            throw new IllegalStateException("Removed more keys than wanted"); // sanity check
        }
    }

    /**
     * Removes the given number of bytes starting at the given position from the given node.
     *
     * @param start the start position of the bytes to remove
     * @param length the amount of bytes to remove
     * @param indexedNode the current node to remove from
     * @param cumulativeLength the length of the intervals up to the current node and index
     * @param removedLength the length of the intervals that have already been removed
     * @return the number of bytes that have been removed
     */
    private int remove(int start, int length, IndexedNodeLinkedList indexedNode, int cumulativeLength, int removedLength) {

        int initiallyRemoved = removedLength;
        boolean visitNextChild = true;

        // iterate over all children and keys
        for (; indexedNode.index < indexedNode.node.size; indexedNode.index++) {
            // before i-th child and i-th child.

            // check if we have removed enough
            if (removedLength > length) {
                throw new IllegalStateException("Removed more keys than wanted"); // sanity check
            } else if (removedLength == length) {
                return removedLength - initiallyRemoved;
            }

            // check if we have to visit the next child
            // we don't want to visit the child if we have already visited it but had to go back because the previous
            // key changed
            if (visitNextChild) {

                // remove from i-th child if start is in front of or in the i-th child, and it exists
                if (indexedNode.node.children[indexedNode.index] != null &&
                    start < cumulativeLength + indexedNode.node.childLengths[indexedNode.index]) {

                    // remove from child
                    final int removedInChild = remove(start, length,
                        new IndexedNodeLinkedList(indexedNode, indexedNode.node.children[indexedNode.index], 0),
                        cumulativeLength, removedLength);

                    // update removedLength
                    removedLength += removedInChild;

                    // update childLength of parent accordingly
                    indexedNode.node.childLengths[indexedNode.index] -= removedInChild;

                    // check if we have removed enough
                    if (removedLength == length) {
                        return removedLength - initiallyRemoved;
                    } else if (removedLength > length) {
                        throw new IllegalStateException("Removed more keys than wanted"); // sanity check
                    }
                }

                cumulativeLength += indexedNode.node.childLengths[indexedNode.index];
            } else {
                visitNextChild = true;
            }

            // get the i-th key
            Interval key = indexedNode.node.keys[indexedNode.index];

            // the key might not exist anymore
            if (key == null) {
                return removedLength - initiallyRemoved;
            }

            // if start is in the i-th key we just have to shorten the interval
            if (start > cumulativeLength && start < cumulativeLength + key.length()) {

                // calculate the new length of the key
                final int newLength = start - cumulativeLength;

                // update cumulativeLength before updating the key
                cumulativeLength += key.length();

                // update the key
                indexedNode.node.keys[indexedNode.index] = new Interval(key.start(), newLength);

                // update removedLength
                removedLength += key.length() - newLength;

                // continue with next key
                continue;
            }

            // if start is in front of or at the start of the i-th key we have to remove the key
            if (start <= cumulativeLength) {

                // if the key is longer than the length to be removed we just have to shorten the key
                if (key.length() > length - removedLength) {

                    final int newLength = key.length() - (length - removedLength);
                    final int newStart = key.start() + (key.length() - newLength);

                    // update the key
                    indexedNode.node.keys[indexedNode.index] = new Interval(newStart, newLength);

                    // update removedLength
                    removedLength += key.length() - newLength;

                    // we are done
                    return removedLength - initiallyRemoved;
                }

                // if we are in a leaf node we can just remove the key
                if (indexedNode.node.isLeaf()) {

                    ensureSize(indexedNode);

                    // move all keys after the removed key to the left
                    System.arraycopy(indexedNode.node.keys, indexedNode.index + 1,
                        indexedNode.node.keys, indexedNode.index, indexedNode.node.size - indexedNode.index - 1);

                    // remove (duplicated) last key
                    indexedNode.node.keys[indexedNode.node.size - 1] = null;

                    // update size
                    indexedNode.node.size--;

                    // update removedLength
                    removedLength += key.length();

                    // the next key moved one index to the left
                    indexedNode.index--;

                } else { // remove key from inner node

                    // try to replace with rightmost key of left child
                    if (indexedNode.node.children[indexedNode.index].size >= degree) {
                        final Interval removedKey = removeRightMostKey(new IndexedNodeLinkedList(indexedNode,
                            indexedNode.node.children[indexedNode.index], 0));

                        // update childLength of current node
                        indexedNode.node.childLengths[indexedNode.index] -= removedKey.length();

                        // update key
                        indexedNode.node.keys[indexedNode.index] = removedKey;

                        // update removedLength
                        removedLength += key.length();

                        // try to replace with leftmost key of right child
                    } else if (indexedNode.node.children[indexedNode.index + 1].size >= degree) {
                        final Interval removedKey = removeLeftMostKey(new IndexedNodeLinkedList(indexedNode,
                            indexedNode.node.children[indexedNode.index + 1], 0));

                        // update childLength of current node
                        indexedNode.node.childLengths[indexedNode.index + 1] -= removedKey.length();

                        // update key
                        indexedNode.node.keys[indexedNode.index] = removedKey;

                        // update removedLength
                        removedLength += key.length();

                        cumulativeLength += removedKey.length();

                        // we might have to remove the new key as well -> go back
                        indexedNode.index--;
                        visitNextChild = false; // we don't want to remove from the previous child again

                        continue;

                        // if both children have only degree - 1 keys we have to merge them and remove the key from the merged node
                    } else {

                        // save the length of the right child before merging because we have to add it to the
                        // cumulative length later
                        final int rightNodeLength = indexedNode.node.childLengths[indexedNode.index + 1];

                        ensureSize(indexedNode);

                        // merge the two children
                        mergeWithRightSibling(new IndexedNodeLinkedList(indexedNode,
                            indexedNode.node.children[indexedNode.index], 0));

                        // remove the key from the merged node
                        int removedInChild = remove(start, length, new IndexedNodeLinkedList(indexedNode,
                            indexedNode.node.children[indexedNode.index], degree - 1),
                            cumulativeLength, removedLength);

                        // update childLength of current node
                        indexedNode.node.childLengths[indexedNode.index] -= removedInChild;

                        // update removedLength
                        removedLength += removedInChild;

                        // add the right child to the cumulative length
                        cumulativeLength += rightNodeLength;

                        // merging with right child shifted the keys to the left -> we have to visit the previous key again
                        indexedNode.index--;
                        visitNextChild = false; // we don't want to remove from the previous child again
                    }

                }

            }

            // update cumulativeLength after visiting the i-th key
            cumulativeLength += key.length();

        } // only the last child is left

        // check if we have removed enough
        if (removedLength > length) {
            throw new IllegalStateException("Removed more keys than wanted"); // sanity check
        } else if (removedLength == length) {
            return removedLength - initiallyRemoved;
        }

        // remove from the last child if start is in front of or in the i-th child, and it exists
        if (indexedNode.node.children[indexedNode.node.size] != null &&
            start <= cumulativeLength + indexedNode.node.childLengths[indexedNode.node.size]) {

            // remove from child
            int removedInChild = remove(start, length, new IndexedNodeLinkedList(indexedNode,
                indexedNode.node.children[indexedNode.node.size], 0), cumulativeLength, removedLength);

            // update childLength of parent accordingly
            indexedNode.node.childLengths[indexedNode.node.size] -= removedInChild;

            // update removedLength
            removedLength += removedInChild;
        }

        return removedLength - initiallyRemoved;
    }

    /**
     * Removes the rightmost key of the given node if it is a leaf.
     * Otherwise, it will remove the rightmost key of the last child.
     *
     * @param indexedNode the node to remove the rightmost key from.
     * @return the removed key.
     */
    private Interval removeRightMostKey(IndexedNodeLinkedList indexedNode) {

        throw new UnsupportedOperationException("Not implemented yet"); //TODO H3 d): remove if implemented
    }

    /**
     * Removes the leftmost key of the given node if it is a leaf.
     * Otherwise, it will remove the leftmost key of the first child.
     *
     * @param indexedNode the node to remove the leftmost key from.
     * @return the removed key.
     */
    private Interval removeLeftMostKey(IndexedNodeLinkedList indexedNode) {

        if (indexedNode.node.children[0].isLeaf()){
            indexedNode.node.children[0] = null;
        }
        return null;
    }

    /**
     * Ensures that the given node has at least degree keys if it is not the root.
     * If the node has less than degree keys, it will try to rotate a key from a sibling or merge with a sibling.
     *
     * @param indexedNode the node to ensure the size of.
     */
    private void ensureSize(IndexedNodeLinkedList indexedNode) {

        throw new UnsupportedOperationException("Not implemented yet"); //TODO H3 c): remove if implemented
    }

    /**
     * Merges the given node with its left sibling.
     * The method ensures that the given indexedNode points to correct node and index after the split.
     *
     * @param indexedNode the node to merge with its left sibling.
     */
    private void mergeWithLeftSibling(IndexedNodeLinkedList indexedNode) {

        throw new UnsupportedOperationException("Not implemented yet"); //TODO H3 b): remove if implemented
    }

    /**
     * Merges the given node with its right sibling.
     * The method ensures that the given indexedNode points to correct node and index after the split.
     *
     * @param indexedNode the node to merge with its right sibling.
     */
    private void mergeWithRightSibling(IndexedNodeLinkedList indexedNode) {

    }
    /**
     * Rotates an interval from the left sibling via the parent to the given node.
     *
     * @param indexedNode the node to rotate to.
     */
    private void rotateFromLeftSibling(IndexedNodeLinkedList indexedNode) {    //jeder node hat array von keys, jeder key hat attribut start und length(intervall),
                                                                                //jeder node hat ein array  von children und childlength
        //linken geschwisterknoten in variable gelegt
        BtrfsNode mostLeftSibling = indexedNode.parent.node.children[indexedNode.parent.index-1];
        //vaterknoten in variable gelegt
        BtrfsNode parent = indexedNode.parent.node;

        //moving our keys in index node one position further (right)
       keyMoving(indexedNode);
        //moving our children one position further to left
       childrenMoveLeft(indexedNode);
        //moving key one left of parent.index into indexed.node
        indexedNode.node.keys[0] = parent.keys[indexedNode.parent.index-1];
        //moving last key of sibling to the parentkey
        parent.keys[indexedNode.parent.index-1] = mostLeftSibling.keys[mostLeftSibling.size-1];
        //changing the childlength of the parent node
        parent.childLengths[indexedNode.parent.index-1] -= (mostLeftSibling.keys[mostLeftSibling.size-1].length()+mostLeftSibling.childLengths
            [mostLeftSibling.size]);
        //changing childlength on parent index, adding node.key.length which was in parentnode before, removed childlength needs to be added on
        parent.childLengths[indexedNode.parent.index] += mostLeftSibling.childLengths[mostLeftSibling.size] + indexedNode.node.keys[0].length();
        //resizing the node size
        indexedNode.node.size++;
        //taking last child of left sibling and connecting it to the indexnode on position 0
        indexedNode.node.children[0] = mostLeftSibling.children[mostLeftSibling.size];
        //same with childlength
        indexedNode.node.childLengths[0] = mostLeftSibling.childLengths[mostLeftSibling.size];
        //deleting the last key, child and childlength of the left sibling
        mostLeftSibling.keys[mostLeftSibling.size-1] = null;
        mostLeftSibling.children[mostLeftSibling.size] = null;
        mostLeftSibling.childLengths[mostLeftSibling.size] = 0;
        //resizing sibling
        mostLeftSibling.size--;
        //incrementing index of indexnode , because we moved the indexnode one to the right
        indexedNode.index++;
    }
    private void keyMoving(IndexedNodeLinkedList indexedNode) {
        //itertieren über die nodes in indexednode
                                                                                                // es gibt immer ein child mehr als keys
        for (int i = indexedNode.node.size; i > 0; i--) {
            //iten key eins nach rechts, i-1ten an stelle i schieben, also eins nach rechts
            indexedNode.node.keys[i] = indexedNode.node.keys[i - 1];
        }
    }
    private void childrenMoveLeft(IndexedNodeLinkedList indexedNode){
        //iterieren über die nodes in indexednode
        for (int i = indexedNode.node.size+1; i >0 ; i--) {
            //childlength eins nach rechts schieben
            indexedNode.node.childLengths[i] = indexedNode.node.childLengths[i-1];
            //children eins nach rechts
            indexedNode.node.children[i] = indexedNode.node.children[i-1];
        }
    }

    /**
     * Rotates an interval from the right sibling via the parent to the given node.
     *
     * @param indexedNode the node to rotate to.
     */
    private void rotateFromRightSibling(IndexedNodeLinkedList indexedNode) {
        BtrfsNode mostRightSibling = indexedNode.parent.node.children[indexedNode.parent.index+1];
        BtrfsNode parent = indexedNode.parent.node;
        //connecting parentkey to indexnode
        indexedNode.node.keys[indexedNode.node.size] = parent.keys[indexedNode.parent.index];
        //connecting 0th node of sibling to parent
        parent.keys[indexedNode.parent.index]= mostRightSibling.keys[0];
        //changing childlength of the parent, bc keys and children are changed (umgehängt)
        parent.childLengths[indexedNode.parent.index]+=mostRightSibling.childLengths[0]+indexedNode.node.keys[indexedNode.node.size].length();
        parent.childLengths[indexedNode.parent.index+1]-=(mostRightSibling.childLengths[0]+mostRightSibling.keys[0].length());
        //resizing indexnode
        indexedNode.node.size++;
        //connecting the 0th child of right sibling to the end of indexnode
        indexedNode.node.children[indexedNode.node.size] = mostRightSibling.children[0];
        //genauso wie kind muss man länge an gleichen spot umhängen
        indexedNode.node.childLengths[indexedNode.node.size] = mostRightSibling.childLengths[0];
        //pushing keys from rightsibling one to left
        for (int i = 0; i < mostRightSibling.size-1; i++) {
            mostRightSibling.keys[i] = mostRightSibling.keys[i+1];
        }
        // moving children and childlength from right sibling one to the right                        //immer ein kind mehr als keys
        for (int i = 0; i < mostRightSibling.size; i++) {
            mostRightSibling.children[i] = mostRightSibling.children[i+1];
            mostRightSibling.childLengths[i] = mostRightSibling.childLengths[i+1];
        }
        //resizing the sibling
        mostRightSibling.size --;
    }

    /**
     * Checks if there are any adjacent intervals that are also point to adjacent bytes in the storage.
     * If there are such intervals, they are merged into a single interval.
     */
    public void shrink() {

        throw new UnsupportedOperationException("Not implemented yet"); //TODO H4 b): remove if implemented
    }

    /**
     * Returns the size of the file.
     * This is the sum of the length of all intervals or the amount of bytes used in the storage.
     *
     * @return the size of the file.
     */
    public int getSize() {
        return size;
    }

    /**
     * Returns the name of the file.
     *
     * @return the name of the file.
     */
    public String getName() {
        return name;
    }
}
