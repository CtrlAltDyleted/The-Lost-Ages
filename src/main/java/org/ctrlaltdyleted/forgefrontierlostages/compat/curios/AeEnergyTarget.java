package org.ctrlaltdyleted.forgefrontierlostages.compat.curios;

final class AeEnergyTarget implements EnergyTransfer.Target {
    interface Storage {
        double maxPower();
        double currentPower();
        double chargeRate();
        double inject(double ae, boolean simulate);
    }

    private final Storage storage;
    private final double aePerFe;

    AeEnergyTarget(Storage storage, double aePerFe) {
        this.storage = storage;
        this.aePerFe = aePerFe;
    }

    @Override
    public int receive(int requestedFe, boolean simulate) {
        if (requestedFe <= 0 || !Double.isFinite(aePerFe) || aePerFe <= 0) return 0;
        double roomAe = Math.max(0, storage.maxPower() - storage.currentPower());
        double rateAe = Math.max(0, storage.chargeRate());
        int roomFe = (int) Math.min(Integer.MAX_VALUE, Math.floor(roomAe / aePerFe));
        int rateFe = (int) Math.min(Integer.MAX_VALUE, Math.floor(rateAe / aePerFe));
        int offeredFe = Math.min(requestedFe, Math.min(roomFe, rateFe));
        if (offeredFe <= 0) return 0;
        double offeredAe = offeredFe * aePerFe;
        double unusedAe = storage.inject(offeredAe, simulate);
        return Math.min(offeredFe,
                (int) Math.floor(Math.max(0, offeredAe - unusedAe) / aePerFe + 1e-6));
    }
}
