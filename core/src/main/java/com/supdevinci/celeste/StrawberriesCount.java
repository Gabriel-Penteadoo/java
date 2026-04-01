package com.supdevinci.celeste;

public class StrawberriesCount{
        private int strawberriesCount;

        public StrawberriesCount() {
            this.strawberriesCount = 0;
        }

        public void addStrawberry() {
            this.strawberriesCount++;
        }

        public int getStrawberriesCount() {
            return this.strawberriesCount;
        }
}
