package com.wkaye.jvote;

import com.johnymuffin.beta.fundamentals.util.Utils;

public enum JVoteEnums {
    DAY {
        public String color() {
            return Utils.formatColor("&e");
        }

    },
    NIGHT {
        public String color() {
            return Utils.formatColor("&8");
        }
    },
    CLEAR {
        public String color() {
            return Utils.formatColor("&f");
        }
    },
    STORMY {
        public String color() {
            return Utils.formatColor("&9");
        }
    };

    public abstract String color();
}
