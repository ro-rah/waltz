import template from "./roadmaps-panel.html";
import {initialiseData} from "../../../common";
import {CORE_API} from "../../../common/services/core-api-utils";
import {mkSelectionOptions} from "../../../common/selector-utils";


const bindings = {
    parentEntityRef: "<"
};


const modes = {
    LOADING: "LOADING",
    LIST: "LIST",
    ADD_SCENARIO: "ADD_SCENARIO",
    VIEW_SCENARIO: "VIEW_SCENARIO"
};


const initialState = {
    modes,
    roadmaps: [],
    scenarios: [],
    selectedScenario: null,
    visibility: {
        mode: modes.LOADING
    }
};


function controller($q, serviceBroker, notification) {
    const vm = initialiseData(this, initialState);

    const loadData = () => {
        const roadmapSelectorOptions = mkSelectionOptions(vm.parentEntityRef);

        vm.visibility.mode = modes.LOADING;

        const roadmapPromise = serviceBroker
            .loadViewData(
                CORE_API.RoadmapStore.findRoadmapsBySelector,
                [ roadmapSelectorOptions ])
            .then(r => vm.roadmaps = r.data);

        const scenarioPromise = serviceBroker
            .loadViewData(
                CORE_API.ScenarioStore.findByRoadmapSelector,
                [ roadmapSelectorOptions ],
                { force: true })
            .then(r => vm.scenarios = r.data);

        return $q
            .all([roadmapPromise, scenarioPromise])
            .then(() => vm.visibility.mode = modes.LIST);
    };


    vm.$onInit = () => {
        loadData();
    };


    // -- INTERACT --

    vm.onAddScenario = (roadmap) => {
        vm.visibility.mode = modes.ADD_SCENARIO;
    };

    vm.onSelectScenario = (scenario, roadmap) => {
        vm.visibility.mode = modes.VIEW_SCENARIO;
        vm.selectedScenario = scenario;
    };

    vm.onCloneScenario = (scenario) => {
        const newName = prompt("Please enter a new name for the scenario", `Clone of ${scenario.name}`);
        if (newName) {
            serviceBroker
                .execute(CORE_API.ScenarioStore.cloneById, [scenario.id, newName])
                .then(() => loadData())
                .then(() => notification.success("Scenario cloned"));
        } else {
            notification.warning("Aborting clone")
        }
    };

    vm.onCancel = () => {
        vm.visibility.mode = modes.LIST;
        vm.selectedScenario = null;
    };
}


controller.$inject = [
    "$q",
    "ServiceBroker",
    "Notification"
];


const component = {
    bindings,
    template,
    controller
};


export default {
    id: "waltzRoadmapsPanel",
    component
};