/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package javaclsimple.tool;

import com.google.common.base.Function;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool to manages Pairs of Futures as Future of pair, and Maps of Futures as
 * Future of Maps
 *
 * @author visme
 */
public class FuturesTools {

    static public <A, B> ListenableFuture<Map<A, B>> allAsMap(final Map<A, ListenableFuture<B>> mapFuture) {
        final List<ListenableFuture<SSPair<A, B>>> lstFuturPair = new ArrayList<ListenableFuture<SSPair<A, B>>>();

        for (Map.Entry<A, ListenableFuture<B>> e : mapFuture.entrySet()) {
            final A key = e.getKey();
            AsyncFunction<B, SSPair<A, B>> function = new AsyncFunction<B, SSPair<A, B>>() {
                @Override
                public ListenableFuture<SSPair<A, B>> apply(B result) throws Exception {
                    return Futures.immediateFuture(new SSPair<A, B>(key, result));
                }
            };
            lstFuturPair.add(Futures.transform(e.getValue(), function));
        }
        return futureToMap(lstFuturPair);
    }

    // Transformation de collection de SSPair en Map
    static private <A, B> Map<A, B> toMap(final Collection<SSPair<A, B>> pairs) {
        if (pairs == null) {
            return null;
        }
        final Map<A, B> map = new HashMap<A, B>();
        for (SSPair<A, B> pair : pairs) {
            if (pair.getFirst() != null) {
                map.put(pair.getFirst(), pair.getSecond());
            }
        }
        return map;
    }

    static private <A, B> ListenableFuture<Map<A, B>> futureToMap(final List<ListenableFuture<SSPair<A, B>>> lstFuturPair) {
        final ListenableFuture<List<SSPair<A, B>>> futureStyles = Futures.allAsList(lstFuturPair); // conserve l'ordre
        return Futures.transform(futureStyles, new FunctionToMap<A, B>());
    }

    static private class FunctionToMap<A, B> implements AsyncFunction<Collection<SSPair<A, B>>, Map<A, B>> {

        @Override
        public ListenableFuture<Map<A, B>> apply(Collection<SSPair<A, B>> pairs) throws Exception {
            return Futures.immediateFuture(toMap(pairs));
        }
    }

    public static <Type extends Iterable<Boolean>> ListenableFuture<Boolean> and(ListenableFuture<Type> input, boolean emptyResult) {
        return Futures.transform(input, new And<Type>(emptyResult));
    }

    public static <Type extends Iterable<ListenableFuture<Boolean>>> ListenableFuture<Boolean> and(Type input, boolean emptyResult) {
        return Futures.transform(Futures.allAsList(input), new And<List<Boolean>>(emptyResult));
    }

    /**
     * Function to transform a collection of booleans into a resulting boolean
     * that is the AND between all of them. It's possible to sepcify a default
     * return value if the input collection is empty.
     *
     * @param <Type>
     */
    public static class And<Type extends Iterable<Boolean>> implements Function<Type, Boolean> {

        private final boolean emptyResult;

        public And(boolean emptyResult) {
            this.emptyResult = emptyResult;
        }

        @Override
        public Boolean apply(Type input) {
            if (input == null) {
                return emptyResult;
            }
            boolean result = true;
            boolean atLeastOne = false;
            for (Boolean elt : input) {
                result = result && elt;
                atLeastOne = true;
            }
            return atLeastOne ? result : emptyResult;
        }
    }

    public static <A, B extends A> ListenableFuture<A> upcast(ListenableFuture<B> input) {
        return Futures.transform(input, new Upcaster<A, B>());
    }

    /**
     * Function to transform a result of a certain type B into a result of a
     * less specific compatible type A.
     *
     * @param <A>
     * @param <B>
     */
    public static class Upcaster<A, B extends A> implements Function<B, A> {

        @Override
        public A apply(B b) {
            return b;
        }
    }

    public static <A, B extends A> ListenableFuture<Collection<A>> collectionUpcast(ListenableFuture<Collection<B>> input) {
        return Futures.transform(input, new CollectionUpcaster<A, B>());
    }

    /**
     * Function to transform a collection of items into another collection of
     * compatible items of a less specific type. The item are the same, but
     * simply, the resulting collection is less specific.
     *
     * @param <OutType>
     * @param <InType>
     */
    public static class CollectionUpcaster<OutType, InType extends OutType> implements Function<Collection<InType>, Collection<OutType>> {

        @Override
        public Collection<OutType> apply(Collection<InType> input) {
            return new ArrayList<OutType>(input);
        }
    }

    public static <Type> ListenableFuture<Type> singleResult(ListenableFuture<Collection<Type>> input, boolean allowNotFound, boolean allowSeveral) {
        return Futures.transform(input, new SingleResult<Type>(allowNotFound, allowSeveral));
    }

    public static <Type> ListenableFuture<Type> singleResult(ListenableFuture<Collection<Type>> input, boolean allowSeveral) {
        return Futures.transform(input, new SingleResult<Type>(allowSeveral));
    }

    /**
     * <div>Function to return only one element from a collection result, with
     * optionnal ability to allow empty result and multiple results. </div>
     *
     * <div>The allow not found mode configure the behavior in case of no
     * available results : if true, the function returns null ; if false an
     * exception is thrown.</div>
     *
     * <div> The allow several mode configure the behavior in case of multiple
     * result : if true, an arbitrary element is returned ; if false an
     * exception is thrown.</div>
     *
     * @param <Type>
     */
    public static class SingleResult<Type> implements Function<Collection<Type>, Type> {

        private final boolean allowNotFound;
        private final boolean allowSeveral;

        public SingleResult(boolean allowNotFound, boolean allowSeveral) {
            this.allowNotFound = allowNotFound;
            this.allowSeveral = allowSeveral;
        }

        public SingleResult(boolean allowSeveral) {
            this(false, allowSeveral);
        }

        public SingleResult() {
            this(true, true);
        }

        @Override
        public Type apply(Collection<Type> input) {
            if (input == null || input.isEmpty()) {
                if (allowNotFound) {
                    return null;
                } else {
                    throw new RuntimeException("no result found");
                }
            } else if (input.size() == 1 || allowSeveral) {
                return input.iterator().next();
            } else {
                throw new RuntimeException("too much results found");
            }
        }
    }
}
