import { registerPlugin } from "@capacitor/core";
const CapacitorGoogleMaps = registerPlugin("CapacitorGoogleMaps", {
    web: () => import("./web").then((m) => new m.CapacitorGoogleMapsWeb()),
});
CapacitorGoogleMaps.addListener("isMapInFocus", (data) => {
    var _a;
    const x = data.x;
    const y = data.y;
    const elem = document.elementFromPoint(x, y);
    const internalId = (_a = elem === null || elem === void 0 ? void 0 : elem.dataset) === null || _a === void 0 ? void 0 : _a.internalId;
    const mapInFocus = internalId === data.mapId;
    // const mapElement = Array.from(
    //   document.querySelectorAll<HTMLElement>("[data-internal-id]")
    // ).find((el) => el.dataset.internalId === data.mapId);
    // const mapElementAtPoint = elem?.closest<HTMLElement>("[data-internal-id]");
    // const interactiveElementAtPoint = elem?.closest(
    //   'a,button,input,textarea,select,[role="button"],[data-map-control="true"]'
    // );
    // const pointInsideMap =
    //   mapElement &&
    //   (() => {
    //     const rect = mapElement.getBoundingClientRect();
    //     return x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom;
    //   })();
    // const mapInFocus = Boolean(
    //   mapElementAtPoint?.dataset?.internalId === data.mapId ||
    //     (pointInsideMap && !interactiveElementAtPoint)
    // );
    CapacitorGoogleMaps.dispatchMapEvent({ id: data.mapId, focus: mapInFocus });
});
export { CapacitorGoogleMaps };
//# sourceMappingURL=implementation.js.map