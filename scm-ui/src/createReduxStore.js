import thunk from 'redux-thunk';
import logger from 'redux-logger';
import { createStore, compose, applyMiddleware, combineReducers } from 'redux';
import { routerReducer, routerMiddleware } from 'react-router-redux';

import page from './modules/page';

function createReduxStore(history) {
  const composeEnhancers = window.__REDUX_DEVTOOLS_EXTENSION_COMPOSE__ || compose;

  const reducer = combineReducers({
    router: routerReducer,
    page
  });

  return createStore(
    reducer,
    composeEnhancers(applyMiddleware(routerMiddleware(history), thunk, logger))
  );
}

export default createReduxStore;
