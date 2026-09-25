package org.ctrlaltdyleted.forgefrontierlostages.compat.curios;

public final class EnergyTransferRegression {
    private EnergyTransferRegression() {}

    public static void main(String[] args) {
        check(1000, 5000, 5000, 1000, 4000, 1000);
        check(64000, 50, 5000, 50, 0, 50);
        check(1000, 5000, 10, 10, 4990, 10);
        check(1000, 5000, 0, 0, 5000, 0);
        check(1000, 0, 5000, 0, 0, 0);
        shortExtraction();
        changedTargetCapacity();
        aePoweredItem();
        aePoweredItemNearFull();
    }

    private static void check(int limit, int sourceAmount, int targetSpace,
                              int expectedReceived, int expectedSource, int expectedTarget) {
        FakeSource source = new FakeSource(sourceAmount);
        FakeTarget target = new FakeTarget(targetSpace);
        int received = EnergyTransfer.charge(limit, source, target);
        require(received == expectedReceived && source.amount == expectedSource
                && target.stored == expectedTarget, "transfer amount or accounting");
        require(sourceAmount - source.amount == target.stored, "energy conservation");
    }

    private static void shortExtraction() {
        FakeSource source = new FakeSource(100);
        source.actualLimit = 7;
        FakeTarget target = new FakeTarget(100);
        require(EnergyTransfer.charge(100, source, target) == 7, "short extraction");
        require(source.amount == 93 && target.stored == 7, "short extraction accounting");
    }

    private static void changedTargetCapacity() {
        FakeSource source = new FakeSource(100);
        FakeTarget target = new FakeTarget(100);
        target.actualLimit = 3;
        require(EnergyTransfer.charge(100, source, target) == 3, "short insertion");
        require(source.amount == 97 && target.stored == 3, "refund accounting");
    }

    private static void aePoweredItem() {
        FakeSource source = new FakeSource(1000);
        FakeAeStorage storage = new FakeAeStorage(100, 0, 25);
        int received = EnergyTransfer.charge(1000, source, new AeEnergyTarget(storage, 0.5));
        require(received == 50, "AE item transfer limit in FE");
        require(source.amount == 950 && storage.current == 25, "FE to AE accounting");
    }

    private static void aePoweredItemNearFull() {
        FakeSource source = new FakeSource(1000);
        FakeAeStorage storage = new FakeAeStorage(100, 99.75, 25);
        int received = EnergyTransfer.charge(1000, source, new AeEnergyTarget(storage, 0.5));
        require(received == 0 && source.amount == 1000 && storage.current == 99.75,
                "fractional FE must not be spent or created");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FakeSource implements EnergyTransfer.Source {
        int amount;
        int actualLimit = Integer.MAX_VALUE;

        FakeSource(int amount) { this.amount = amount; }

        @Override
        public long extract(int requested, boolean simulate) {
            int extracted = Math.min(amount, Math.min(requested, simulate ? Integer.MAX_VALUE : actualLimit));
            if (!simulate) amount -= extracted;
            return extracted;
        }

        @Override
        public void refund(long amount) { this.amount += (int) amount; }
    }

    private static final class FakeTarget implements EnergyTransfer.Target {
        int space;
        int stored;
        int actualLimit = Integer.MAX_VALUE;

        FakeTarget(int space) { this.space = space; }

        @Override
        public int receive(int requested, boolean simulate) {
            int received = Math.min(space - stored, Math.min(requested,
                    simulate ? Integer.MAX_VALUE : actualLimit));
            if (!simulate) stored += received;
            return received;
        }
    }

    private static final class FakeAeStorage implements AeEnergyTarget.Storage {
        final double max;
        final double rate;
        double current;

        FakeAeStorage(double max, double current, double rate) {
            this.max = max;
            this.current = current;
            this.rate = rate;
        }

        @Override
        public double maxPower() { return max; }

        @Override
        public double currentPower() { return current; }

        @Override
        public double chargeRate() { return rate; }

        @Override
        public double inject(double ae, boolean simulate) {
            double accepted = Math.min(ae, Math.min(max - current, rate));
            if (!simulate) current += accepted;
            return ae - accepted;
        }
    }
}
