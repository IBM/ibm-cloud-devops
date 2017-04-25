/*
    <notice>

    Copyright 2016, 2017 IBM Corporation

     Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
    The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

    </notice>
 */
package com.ibm.devops.dra;

/**
 * Object for creating dummy test result file following mocha format
 * Created by Xunrong Li on 8/9/16.
 */
public class TestResultModel{

    // sub-model for stats
    public static class Stats {
        private Integer suites;
        private Integer tests;
        private Integer passes;
        private Integer pending;
        private Integer failures;
        private String start;
        private String end;
        private Long duration;

        public Stats(Integer suites,
                     Integer tests,
                     Integer passes,
                     Integer pending,
                     Integer failures,
                     String start,
                     String end,
                     Long duration) {
            this.suites = suites;
            this.tests = tests;
            this.passes = passes;
            this.pending = pending;
            this.failures = failures;
            this.start = start;
            this.end = end;
            this.duration = duration;
        }
    }

    // sub-model for test
    public static class Test {
        private String title;
        private String fullTitle;
        private Long duration;
        private Integer currentRetry;
        private Object err;

        public Test(String title,
                    String fullTitle,
                    Long duration,
                    Integer currentRetry,
                    Object err) {
            this.title = title;
            this.fullTitle = fullTitle;
            this.duration = duration;
            this.currentRetry = currentRetry;
            this.err = err;
        }
    }

    private Stats stats;
    private Test tests[];
    private String pending[];
    private String failures[];
    private String passes[];

    public TestResultModel(Stats stats,
                           Test[] tests,
                           String[] pending,
                           String[] failures,
                           String[] passes) {
        this.stats = stats;
        this.tests = tests;
        this.pending = pending;
        this.failures = failures;
        this.passes = passes;
    }
}
