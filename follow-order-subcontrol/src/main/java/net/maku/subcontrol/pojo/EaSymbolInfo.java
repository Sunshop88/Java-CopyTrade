package net.maku.subcontrol.pojo;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import online.mtapi.mt4.ConGroupSec;
import online.mtapi.mt4.SymbolInfo;

/**
 * @author hipil
 */
@Data
@Slf4j
public class EaSymbolInfo {

    public EaSymbolInfo(SymbolInfo symbolInfo, ConGroupSec conGroupSec) {
        tradeTickValue = symbolInfo.Ex.tick_value;
        tradeTickSize = symbolInfo.Ex.tick_size;
        point = symbolInfo.Ex.point;
        digits = symbolInfo.Ex.digits;

        minVolume = conGroupSec.lot_min / 100.0;
        maxVolume = conGroupSec.lot_max / 100.0;
        stepVolume = conGroupSec.lot_step / 100.0;
    }

    Double tradeTickValue;
    Double tradeTickSize;
    Double point;
    Integer digits;

    Double minVolume;
    Double maxVolume;
    Double stepVolume;

}
