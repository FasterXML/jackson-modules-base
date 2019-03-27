package perf;

class TestPojo
{
    public int a = 1, b;
    public String name = "Something";
    public Value value1, value2;

    public TestPojo() { }
    public TestPojo(int a, int b,
            String name, Value v) {
        this.a = a;
        this.b = b;
        this.name = name;
        this.value1 = v;
        this.value2 = v;
    }
}