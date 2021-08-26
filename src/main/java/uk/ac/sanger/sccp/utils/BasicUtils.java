package uk.ac.sanger.sccp.utils;

import java.util.*;
import java.util.function.*;
import java.util.regex.Pattern;
import java.util.stream.*;

/**
 * Much copied from the corresponding class in CGAP lims
 * @author dr6
 */
public class BasicUtils {
    /**
     * The pattern used in {@link #trimAndRequire} to identify runs of whitespace
     */
    private static final Pattern RUN_OF_WHITESPACE = Pattern.compile("\\s+");

    private BasicUtils() {}

    /**
     * Returns the first (if any) non-null value.
     * If {@code a} is non-null, returns {@code a}; otherwise returns {@code b}
     * @param a first value
     * @param b second value
     * @param <T> type of value
     * @return {@code a} if it is non-null, otherwise {@code b}
     */
    public static <T> T coalesce(T a, T b) {
        return (a==null ? b : a);
    }

    /**
     * Returns a string representation of the given object.
     * If it is a string it will be in quote marks and unprintable
     * characters will be shown as unicode insertions.
     * @param o object to represent
     * @return a string
     */
    public static String repr(Object o) {
        if (o==null) {
            return "null";
        }
        if (o instanceof CharSequence) {
            return StringRepr.repr((CharSequence) o);
        }
        if (o instanceof Character) {
            return StringRepr.repr((char) o);
        }
        return o.toString();
    }

    /**
     * Reprs each item in a stream and returns a joined string.
     * @param stream a stream of strings
     * @return a comma-space-separated string in square brackets.
     */
    public static String reprStream(Stream<String> stream) {
        return stream.map(BasicUtils::repr).collect(Collectors.joining(", ", "[", "]"));
    }

    /**
     * Reprs each item in a collection and returns a joined string.
     * If the collection is null, returns {@code "null"}
     * @param items a collection of strings
     * @return a comma-space-separated string in square brackets.
     */
    public static String reprCollection(Collection<String> items) {
        if (items==null) {
            return "null";
        }
        return reprStream(items.stream());
    }

    /**
     * Pluralise a message with {@link MessageVar} and add an unordered list.
     * @param template the {@code MessageVar} template
     * @param items the items
     * @param stringFn an optional function to convert the items to strings
     * @param <E> the type of items being listed
     * @return a string including the message, listing the items
     */
    public static <E> String messageAndList(String template, Collection<? extends E> items,
                                            Function<? super E, String> stringFn) {
        return StringUtils.messageAndList(template, items, stringFn);
    }

    /**
     * Using {@link MessageVar} pluralise a message with a template.
     * @param template a template with substitutions baked in
     * @param number the number indicating whether the message should be pluralised or singularised
     * @return the processed string
     */
    public static String pluralise(String template, int number) {
        return MessageVar.process(template, number);
    }

    /**
     * Pluralise a message with {@link MessageVar} and add an unordered list.
     * @param template the {@code MessageVar} template
     * @param items the items
     * @return a string including the message, listing the items
     */
    public static String messageAndList(String template, Collection<?> items) {
        return StringUtils.messageAndList(template, items, null);
    }

    /**
     * Do two collections have the same contents (maybe in a different order)?
     * If the collections contain repetitions, this method does <i>not</i> check
     * that they have the same number of repetitions.
     * @param a a collection
     * @param b a collection
     * @return {@code true} if the two collections have the same size and contents
     */
    @SuppressWarnings("SuspiciousMethodCalls")
    public static boolean sameContents(Collection<?> a, Collection<?> b) {
        if (a==b) {
            return true;
        }
        if (a instanceof Set && b instanceof Set) {
            return a.equals(b);
        }
        if (a==null || b==null || a.size()!=b.size()) {
            return false;
        }
        if (a.size() <= 3) {
            return (a.containsAll(b) && b.containsAll(a));
        }
        if (!(a instanceof Set)) {
            a = new HashSet<>(a);
        }
        if (!(b instanceof Set)) {
            b = new HashSet<>(b);
        }
        return a.equals(b);
    }

    /**
     * Creates a new arraylist using the argument as its contents.
     * If <tt>items</tt> is null, the new list will be empty.
     * If <tt>items</tt> contains objects, the new list will contain those objects.
     * @param items the contents for the new list
     * @param <E> the content type of the list
     * @return a new arraylist
     */
    public static <E> ArrayList<E> newArrayList(Iterable<? extends E> items) {
        if (items==null) {
            return new ArrayList<>();
        }
        if (items instanceof Collection) {
            //noinspection unchecked
            return new ArrayList<>((Collection<? extends E>) items);
        }
        ArrayList<E> list = new ArrayList<>();
        items.forEach(list::add);
        return list;
    }

    /**
     * Collector that produces a {@code LinkedHashSet} (an insertion-ordered set).
     * @param <T> the type of elements
     * @return a collector
     */
    public static <T> Collector<T, ?, LinkedHashSet<T>> toLinkedHashSet() {
        return Collectors.toCollection(LinkedHashSet::new);
    }

    /**
     * Collector to a map supplied by a factory, using {@link #illegalStateMerge}
     * @param <T> the type of the input elements
     * @param <K> the output type of the key mapping function
     * @param <U> the output type of the value mapping function
     * @param <M> the type of the resulting {@code Map}
     * @param keyMapper a mapping function to produce keys
     * @param valueMapper a mapping function to produce values
     * @param mapFactory a supplier providing a new empty {@code Map}
     *                   into which the results will be inserted
     * @return a {@code Collector} which collects elements into a {@code Map}
     *             whose keys are the result of applying a key mapping function to the input
     *             elements, and whose values are the result of applying a value mapping
     *             function to all input elements
     */
    public static <T, K, U, M extends Map<K, U>> Collector<T, ?, M> toMap(Function<? super T, ? extends K> keyMapper,
                                                                          Function<? super T, ? extends U> valueMapper,
                                                                          Supplier<M> mapFactory) {
        return Collectors.toMap(keyMapper, valueMapper, illegalStateMerge(), mapFactory);
    }

    /**
     * Collector to a map where the values are the input objects
     * @param keyMapper a mapping function to produce keys
     * @param mapFactory a supplier providing a new empty {@code Map}
     *                   into which the results will be inserted
     * @param <T> the type of the input elements
     * @param <K> the output type of the key mapping function
     * @param <M> the type of the resulting {@code Map}
     * @return a {@code Collector} which collects elements into a {@code Map}
     *             whose keys are the result of applying a key mapping function to the input
     *             elements, and whose values are input elements
     */
    public static <T, K, M extends Map<K, T>> Collector<T, ?, M> toMap(Function<? super T, ? extends K> keyMapper,
                                                                       Supplier<M> mapFactory) {
        return Collectors.toMap(keyMapper, Function.identity(), illegalStateMerge(), mapFactory);
    }

    /**
     * Collector to a hashmap where the values are the input objects
     * @param keyMapper a mapping function to produce keys
     * @param <T> the type of the input elements
     * @param <K> the output type of the key mapping function
     * @return a {@code Collector} which collects elements into a {@code Map}
     *             whose keys are the result of applying a key mapping function to the input
     *             elements, and whose values are input elements
     */
    public static <T, K> Collector<T, ?, HashMap<K,T>> toMap(Function<? super T, ? extends K> keyMapper) {
        return Collectors.toMap(keyMapper, Function.identity(), illegalStateMerge(), HashMap::new);
    }
    /**
     * A binary operator that throws an illegal state exception. Used as the merge function for collecting
     * to a map whose incoming keys are expected to be unique.
     * @param <U> the type of value
     * @return a binary operator that throws an {@link IllegalStateException}
     */
    public static <U> BinaryOperator<U> illegalStateMerge() {
        return (a, b) -> {throw new IllegalStateException("Duplicate keys found in map.");};
    }

    /**
     * Gets a describer to help generate the toString description for an object.
     * @param name the name of the object (e.g. its type)
     * @return a describer
     */
    public static ObjectDescriber describe(String name) {
        return new ObjectDescriber(name);
    }

    /**
     * Gets a describer to help generate the toString description for an object.
     * @param object the object being described
     * @return a describer
     */
    public static ObjectDescriber describe(Object object) {
        return describe(object.getClass().getSimpleName());
    }

    /**
     * Trims a string, replaces runs of whitespace with a space, and checks that it is non-null and nonempty.
     * @param text the string
     * @return the adjusted string
     * @exception IllegalArgumentException if the string is null or empty (after trimming)
     */
    public static String trimAndRequire(String text, String message) throws IllegalArgumentException {
        if (text==null) {
            throw new IllegalArgumentException(message);
        }
        text = RUN_OF_WHITESPACE.matcher(text.trim()).replaceAll(" ");
        if (text.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }

    /**
     * Predicate for filtering out duplicates from a stream.
     * This uses a simple hashset to track what was seen: it is not guaranteed
     * to work for a parallel stream.
     * The keys (the results of the supplied function) must be hashable.
     * @param function The function to extract the keys from the objects in the stream.
     * @param <T> The stream type.
     * @param <V> The type of key used to distinguish objects in the stream.
     * @return a predicate for filtering out duplicates.
     */
    public static <T, V> Predicate<T> distinctBySerial(Function<? super T, V> function) {
        final Set<V> seen = new HashSet<>();
        return x -> seen.add(function.apply(x));
    }

    /**
     * Predicate for filtering out duplicates in a string case insensitively.
     * This uses a simple hashset to track what was seen: it is not guaranteed
     * to work for a parallel stream.
     * Case insensitivity for the purposes of this method means that the strings are equal
     * when converted to upper case.
     * @return a predicate for filtering out duplicate strings, case insensitively.
     */
    public static Predicate<String> distinctUCSerial() {
        final Set<String> seen = new HashSet<>();
        return x -> seen.add(x==null ? null : x.toUpperCase());
    }
}
