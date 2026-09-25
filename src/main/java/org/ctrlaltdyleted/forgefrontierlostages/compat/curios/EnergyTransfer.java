package org.ctrlaltdyleted.forgefrontierlostages.compat.curios;

final class EnergyTransfer {
    interface Source {
        long extract(int amount, boolean simulate);
        void refund(long amount);
    }

    interface Target {
        int receive(int amount, boolean simulate);
    }

    private EnergyTransfer() {}

    static int charge(int limit, Source source, Target target) {
        int accepted = target.receive(limit, true);
        if (accepted <= 0) return 0;
        long available = source.extract(Math.min(limit, accepted), true);
        if (available <= 0) return 0;
        int simulated = target.receive((int) Math.min(available, accepted), true);
        if (simulated <= 0) return 0;
        long extracted = source.extract(simulated, false);
        if (extracted <= 0) return 0;
        int received = target.receive((int) extracted, false);
        if (received < extracted) source.refund(extracted - received);
        return received;
    }
}
