jest.mock('../Actions.es6');
import {viewBuildStep} from '../Actions.es6'
import React from 'react';
import {shallow, mount} from 'enzyme';
import BuildStepRedux, {BuildStep} from '../BuildStep.es6'
import {MockStore} from './TestSupport.es6'


const details = newAttributes => Object.assign({stepId: 1, state: 'success', name: 'fooStep'}, newAttributes)

describe("BuildStep rendering", ()=>{

  it("should render all step information", ()=>{
    let input = details();

    let component = shallow(<BuildStep buildId={1} step={input}/>);

    expect(component.find('.stepName').text()).toEqual('fooStep');
    expect(component.find('.buildStep').hasClass('success')).toBe(true);
  });

  it("should render failed step state", ()=>{
    let component = shallow(<BuildStep buildId={1} step={details({state: "failed"})}/>);
    expect(component.find('.buildStep').hasClass('failed')).toBe(true);
  });

  it("should render running step state", ()=>{
    let component = shallow(<BuildStep buildId={1} step={details({state: "running"})}/>);
    expect(component.find('.buildStep').hasClass('running')).toBe(true);
  });

  it("should render pending step state", ()=>{
    let component = shallow(<BuildStep buildId={1} step={details({state: "pending"})}/>);
    expect(component.find('.buildStep').hasClass('pending')).toBe(true);
  });

  it("should render link if step has substeps", ()=>{
    let substeps = {steps: [{stepId: "1.1"}]}

    let component = shallow(<BuildStep buildId={1} step={details(substeps)} />)

    expect(component.find('.goIntoStepLink').length).toBe(1);
  })

})

describe("BuildStep wiring", ()=> {
  it("should dispatch go into step action on link click", () => {
    let dispatchMock = jest.fn();
    let storeMock = MockStore({}, dispatchMock);
    let substeps = {steps: [{stepId: "1-1"}]}
    viewBuildStep.mockReturnValue({type: "stepInto"});

    let component = mount(<BuildStepRedux buildId={1} step={details(substeps)} store={storeMock}/>)
    component.find('.goIntoStepLink').simulate('click');

    expect(dispatchMock).toBeCalledWith({type: "stepInto"});
  })
})