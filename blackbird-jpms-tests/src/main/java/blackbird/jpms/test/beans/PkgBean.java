package blackbird.jpms.test.beans;

// Deliberately package-private, in a foreign named module: the codec
// defines in blackbird's own module context and reaches the members only
// through handles databind's fixAccess made accessible (see
// JpmsPackagePrivateTest).
class PkgBean {
    private int count;
    private String name;

    public int getCount() { return count; }
    public void setCount(int v) { count = v; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
}
