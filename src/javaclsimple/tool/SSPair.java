/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package javaclsimple.tool;

import java.io.Serializable;

/**
 *
 * @author stephane
 */
public class SSPair<A, B> implements Serializable {

    private A first;
    private B second;

    public SSPair(A first, B second) {
        super();
        this.first = first;
        this.second = second;
    }

    public SSPair(SSPair<A, B> pair) {
        super();
        this.first = pair.getFirst();
        this.second = pair.getSecond();
    }

    @Override
    public int hashCode() {
        int hashFirst = first != null ? first.hashCode() : 0;
        int hashSecond = second != null ? second.hashCode() : 0;

        return (hashFirst + hashSecond) * hashSecond + hashFirst;
    }

    @Override
    public boolean equals(Object other) {
        if (other != null) {
            if (other instanceof SSPair) {
                SSPair<A, B> otherPair = (SSPair<A, B>) other;
                return ((this.first == otherPair.getFirst() || (this.first != null && otherPair.getFirst() != null && this.first.equals(otherPair.getFirst())))
                        && (this.second == otherPair.getSecond() || (this.second != null && otherPair.getSecond() != null && this.second.equals(otherPair.getSecond()))));
            }
        }
        return false;
    }

    @Override
    public String toString() {
        //return "(" + first + ", " + second + ")";
        return first + "";
    }

    public A getFirst() {
        return first;
    }

    public void setFirst(A first) {
        this.first = first;
    }

    public B getSecond() {
        return second;
    }

    public void setSecond(B second) {
        this.second = second;
    }
}
