export const ADD_SUMMARIES = "addBuildSummaries";
export const CHANGE_SUMMARY = "changeBuildSummary";
export const ADD_CONFIGURATION = "addConfiguration";

// SUMMARY
export const addBuildSummary = (summary) => {
    return {type: ADD_SUMMARIES, summaries: summary};
};

// SUMMARY
export const changeBuildSummary = (buildId, newAttributes) => {
    return {type: CHANGE_SUMMARY, buildId: buildId, newAttributes: newAttributes};
};

//CONFIG
export const addConfiguration = config => {
    return {type: ADD_CONFIGURATION, config: config};
};


