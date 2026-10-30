package tools.jackson.module.blackbird;

import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = BeanWithBuilder.Builder.class)
public class BeanWithBuilder extends SomeBean {
    private BeanWithBuilder() { }

    public static class Builder {
        private final BeanWithBuilder bean = new BeanWithBuilder();

        public Builder withPropA(int propA) {
            bean.setPropA(propA);
            return this;
        }

        public Builder withPropB(String propB) {
            bean.setPropB(propB);
            return this;
        }

        public Builder withPropC(long propC) {
            bean.setPropC(propC);
            return this;
        }

        public Builder withPropD(SomeBean propD) {
            bean.setPropD(propD);
            return this;
        }

        public Builder withPropE(SomeEnum propE) {
            bean.setPropE(propE);
            return this;
        }

        public BeanWithBuilder build() {
            return bean;
        }
    }
}
