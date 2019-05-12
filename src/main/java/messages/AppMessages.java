package messages;

import java.io.Serializable;

public interface AppMessages {

    public static final String BACKEND_REGISTRATION = "BackendRegistration";

    public static class Command implements Serializable {
        private JobMessage jobMessage;

        public Command(JobMessage jobMessage) {
            this.jobMessage = jobMessage;
        }

        public JobMessage getJobMessage() {
            return this.jobMessage;
        }
    }

    public static class Event implements Serializable {
        private JobMessage jobMessage;

        public Event(JobMessage jobMessage) {
            this.jobMessage = jobMessage;
        }

        public JobMessage getJobMessage() {
            return this.jobMessage;
        }
    }

    public static class JobMessage implements Serializable {
        private int counter;
        private String payload;

        public JobMessage(int counter) {
            this.counter = counter;
            this.payload = "hello-" + counter;
        }

        public String getPayload() {
            return payload;
        }

        @Override
        public String toString() {
            return this.payload;
        }

        public void setPayload(String payload) {
            this.payload = payload;
        }

        public int getCounter() {
            return counter;
        }
    }

    public static class ResultMessage implements Serializable {
        private String text;

        public ResultMessage(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return "TransformationResult(" + text + ")";
        }
    }

    public static class FailedMessage implements Serializable {
        private String reason;
        private JobMessage job;

        public FailedMessage(String reason, JobMessage job) {
            this.reason = reason;
            this.job = job;
        }

        @Override
        public String toString() {
            return String.format("FAILED JOB: %s, REASON : %s ", this.job, this.reason);
        }
    }
}
