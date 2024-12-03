package com.wkaye.jvote;

public enum JVoteEnums {
    DAY {
        public String color() {
            return JVoteUtils.formatColor("&e");
        }

    },
    NIGHT {
        public String color() {
            return JVoteUtils.formatColor("&8");
        }
    },
    CLEAR {
        public String color() {
            return JVoteUtils.formatColor("&f");
        }
    },
    SUN {
        public String color() {
            return JVoteUtils.formatColor("&f");
        }
    },
    RAIN {
        public String color() {
            return JVoteUtils.formatColor("&3");
        }
    },
    STORM {
        public String color() {
            return JVoteUtils.formatColor("&9");
        }
    };

    public abstract String color();
}
