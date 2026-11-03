package tools.jackson.module.blackbird;

import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = DoubleBeanWithBuilder.Builder.class)
public class DoubleBeanWithBuilder extends DoubleBean {
    private DoubleBeanWithBuilder() { }

    public static class Builder {
        private final DoubleBeanWithBuilder bean = new DoubleBeanWithBuilder();

        public Builder withId(int id) {
            bean.setId(id);
            return this;
        }

        public Builder withLatitude(double latitude) {
            bean.setLatitude(latitude);
            return this;
        }

        public Builder withLongitude(double longitude) {
            bean.setLongitude(longitude);
            return this;
        }

        public Builder withAltitude(double altitude) {
            bean.setAltitude(altitude);
            return this;
        }

        public Builder withSpeed(double speed) {
            bean.setSpeed(speed);
            return this;
        }

        public Builder withHeading(double heading) {
            bean.setHeading(heading);
            return this;
        }

        public Builder withAccuracy(double accuracy) {
            bean.setAccuracy(accuracy);
            return this;
        }

        public DoubleBeanWithBuilder build() {
            return bean;
        }
    }
}
