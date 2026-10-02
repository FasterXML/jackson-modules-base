package blackbird.jpms.test.beans;

// Deliberately package-private: the codec for this bean must be defined in
// this module's package context, which only works when the generated class
// can resolve its supertype from here.
class PkgBean {
    private int count;
    private String name;

    public int getCount() { return count; }
    public void setCount(int v) { count = v; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
}
