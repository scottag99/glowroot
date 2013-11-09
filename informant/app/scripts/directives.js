/*
 * Copyright 2012-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* global informant, Informant, $, Spinner */

informant.factory('ixButtonGroupControllerFactory', [
  '$q',
  function ($q) {
    return {
      create: function (element) {
        var $element = $(element);
        var alreadyExecuting = false;
        return {
          onClick: function (fn) {
            // handle crazy user clicking on the button
            if (alreadyExecuting) {
              return;
            }
            var $buttonMessage = $element.find('.button-message');
            var $buttonSpinner = $element.find('.button-spinner');
            // in case button is clicked again before message fades out
            $buttonMessage.addClass('hide');
            var spinner = Informant.showSpinner($buttonSpinner);

            var deferred = $q.defer();
            deferred.promise.then(function (success) {
              spinner.stop();
              $buttonMessage.text(success);
              $buttonMessage.removeClass('button-message-error');
              $buttonMessage.addClass('button-message-success');
              Informant.showAndFadeSuccessMessage($buttonMessage);
              alreadyExecuting = false;
            }, function (error) {
              spinner.stop();
              $buttonMessage.text(error);
              $buttonMessage.removeClass('button-message-success');
              $buttonMessage.addClass('button-message-error');
              $buttonMessage.removeClass('hide');
              alreadyExecuting = false;
            });

            alreadyExecuting = true;
            fn({deferred: deferred});
          }
        };
      }
    };
  }
]);

informant.directive('ixButtonGroup', [
  'ixButtonGroupControllerFactory',
  function (ixButtonGroupControllerFactory) {
    return {
      scope: {},
      transclude: true,
      template: '' +
          '<div class="clearfix">' +
          '  <div ng-transclude style="float: left;"></div>' +
          '  <span class="button-spinner inline-block hide" style="float: left;"></span>' +
          // this needs to be div, and it's child needs to be div, for formatting of multi-line messages
          // same as done in ix-button.html template
          '  <div style="overflow-x: hidden;">' +
          '    <div class="button-message hide" style="padding-top: 5px;"></div>' +
          '  </div>' +
          '</div>',
      controller: [
        '$element',
        function ($element) {
          return ixButtonGroupControllerFactory.create($element);
        }
      ]
    };
  }
]);

informant.directive('ixButton', [
  'ixButtonGroupControllerFactory',
  function (ixButtonGroupControllerFactory) {
    return {
      scope: {
        ixLabel: '@',
        ixClick: '&',
        ixShow: '&',
        ixBtnClass: '@',
        ixDisabled: '&'
      },
      templateUrl: function (tElement, tAttrs) {
        if (tAttrs.hasOwnProperty('ixButtonRightAligned')) {
          return 'template/ix-button-right-aligned.html';
        } else {
          return 'template/ix-button.html';
        }
      },
      require: '^?ixButtonGroup',
      link: function (scope, iElement, iAttrs, ixButtonGroup) {
        scope.ngShow = function () {
          return iAttrs.ixShow ? scope.ixShow() : true;
        };
        if (!ixButtonGroup) {
          scope.noGroup = true;
          ixButtonGroup = ixButtonGroupControllerFactory.create(iElement);
        }
        scope.onClick = function () {
          ixButtonGroup.onClick(scope.ixClick);
        };
      }
    };
  }
]);

informant.directive('ixFormGroup', function () {
  return {
    scope: {
      ixType: '@',
      ixLabel: '@',
      ixModel: '=',
      ixWidth: '@',
      ixAddon: '@',
      ixPattern: '@',
      // ixRequired accepts string binding for inline patterns and RegExp binding for scope expressions
      // (same as ngPattern on angular input directive)
      ixRequired: '&',
      ixNumber: '&'
    },
    transclude: true,
    require: '^form',
    templateUrl: 'template/ix-form-group.html',
    link: function (scope, iElement, iAttrs, formCtrl) {
      scope.formCtrl = formCtrl;
      // just need a unique id
      scope.ixId = scope.$id;
      if (!scope.ixType) {
        // default
        scope.ixType = 'text';
      }
      scope.$watch('ixModel', function (newValue) {
        scope.ngModel = newValue;
      });
      scope.$watch('ngModel', function (newValue) {
        if (scope.ixNumber()) {
          if (newValue === '') {
            // map empty string to null number
            scope.ixModel = null;
            return;
          } else {
            // try to convert to number
            var float = parseFloat(newValue);
            if (isNaN(float)) {
              scope.ixModel = newValue;
              return;
            } else {
              scope.ixModel = float;
            }
          }
        } else {
          scope.ixModel = newValue;
        }
      });
      scope.$watch('ixPattern', function (newValue) {
        if (newValue) {
          var match = newValue.match(/^\/(.*)\/$/);
          if (match) {
            scope.ngPattern = new RegExp(match[1]);
          } else {
            scope.ngPattern = scope.$parent.$eval(newValue);
          }
        } else {
          // ngPattern doesn't understand falsy values (maybe it should?)
          // so just pass a pattern that will match everything
          scope.ngPattern = /.?/;
        }
      });
    }
  };
});

informant.directive('ixDatepicker', function () {
  return {
    scope: {
      ixModel: '=',
      ixClass: '@',
      ixId: '@'
    },
    template: '<input type="text" class="form-control" ng-class="ixClass" id="{{ixId}}" style="max-width: 10em;">',
    link: function (scope, iElement, iAttrs) {
      // TODO use bootstrap-datepicker momentjs backend when it's available and then use momentjs's
      // localized format 'moment.longDateFormat.L' both here and when parsing date
      // see https://github.com/eternicode/bootstrap-datepicker/issues/24
      var $input = $(iElement).find('input');
      $input.datepicker({format: 'mm/dd/yyyy', autoclose: true, todayHighlight: true});
      $input.datepicker('setDate', scope.ixModel);
      $input.on('changeDate', function (event) {
        scope.$apply(function () {
          scope.ixModel = event.date;
        });
      });
    }
  };
});

informant.directive('ixInputGroupDropdown', function () {
  return {
    scope: {
      ixModel: '=',
      ixItems: '&',
      ixClass: '@'
    },
    templateUrl: 'template/ix-input-group-dropdown.html',
    // replace is needed in order to not mess up bootstrap css hierarchical selectors
    replace: true,
    link: function (scope, iElement, iAttrs) {
      scope.setItem = function (item) {
        scope.ixModel = item.value;
        scope.ixDisplay = item.display;
      };
      scope.setItem(scope.ixItems()[0]);
      if (scope.ixClass) {
        scope.classes = 'input-group-btn ' + scope.ixClass;
      } else {
        scope.classes = 'input-group-btn';
      }
    }
  };
});

informant.directive('ixNavbarItem', [
  '$location',
  function ($location) {
    return {
      scope: {
        ixDisplay: '@',
        ixItemName: '@',
        ixUrl: '@',
        ixShow: '&'
      },
      // replace is needed in order to not mess up bootstrap css hierarchical selectors
      replace: true,
      templateUrl: 'template/ix-navbar-item.html',
      link: function (scope, iElement, iAttrs) {
        scope.ngShow = function () {
          return iAttrs.ixShow ? scope.ixShow() : true;
        };
        scope.collapseNavbar = function () {
          // need to collapse the navbar in mobile view
          var $navbarCollapse = $('.navbar-collapse');
          $navbarCollapse.removeClass('in');
          $navbarCollapse.addClass('collapse');
        };
      }
    };
  }
]);

informant.directive('ixSetFocus', function () {
  return function (scope, iElement, iAttrs) {
    scope.$watch(iAttrs.ixSetFocus,
        function (newValue) {
          if (newValue) {
            iElement.focus();
          }
        }, true);
  };
});

informant.directive('ixDisplayWhitespace', function () {
  return {
    scope: {
      ixBind: '&'
    },
    link: function (scope, iElement, iAttrs) {
      var text = scope.ixBind();
      iElement.text(text);
      var html = iElement.html();
      html = html.replace('\n', '<em>\\n</em>')
          .replace('\r', '<em>\\r</em>')
          .replace('\t', '<em>\\t</em>');
      html = html.replace('</em><em>', '');
      iElement.html(html);
    }
  };
});

informant.directive('ixSpinner', function () {
  return function (scope, iElement, iAttrs) {
    var spinner;
    var timer;
    iElement.addClass('hide');
    scope.$watch(iAttrs.ixShow,
        function (newValue) {
          if (newValue) {
            if (spinner === undefined) {
              var left = iAttrs.ixSpinnerInline ? 10 : 'auto';
              spinner = new Spinner({ lines: 10, radius: 8, width: 4, left: left });
            }
            // small delay so that if there is an immediate response the spinner doesn't blink
            timer = setTimeout(function () {
              iElement.removeClass('hide');
              spinner.spin(iElement[0]);
            }, 100);
          } else if (spinner !== undefined) {
            clearTimeout(timer);
            iElement.addClass('hide');
            spinner.stop();
          }
        });
  };
});

informant.directive('ixTypeaheadOpenOnEmpty', function () {
  return {
    require: ['typeahead', 'ngModel'],
    link: function (scope, iElement, iAttrs, ctrls) {
      iElement.bind('keyup', function (e) {
        var typeaheadCtrl = ctrls[0];
        var ngModelCtrl = ctrls[1];
        if (e.which === 40 && (ngModelCtrl.$viewValue === undefined || ngModelCtrl.$viewValue === '') &&
            typeaheadCtrl.active === -1) {
          // down arrow key was pressed, and text input is empty, and the typeahead select is not already open
          scope.$apply(function () {
            typeaheadCtrl.getMatches('');
          });
        }
      });
    }
  };
});

informant.directive('ixFormWithPrimaryButton', function () {
  return function (scope, iElement, iAttrs) {
    iElement.on('keypress', 'input', function (e) {
      if (e.which === 13) {
        // NOTE: iElement.find('.btn-primary').click() bypasses the disabled check on the button
        iElement.find('.btn-primary').each(function (index, element) {
          element.click();
        });
      }
    });
  };
});

informant.directive('ixFormAutofocusOnFirstInput', function () {
  return function (scope, iElement, iAttrs) {
    var unregisterWatch = scope.$watch(function () {
      return iElement.find('input').length && iElement.find('input').first().is(':visible');
    }, function (newValue) {
      if (newValue) {
        // setTimeout is needed for IE8
        // (and IE9 sometimes, e.g. on Config > Fine-grained profiling)
        setTimeout(function() {
          iElement.find('input').first().focus();
        });
        unregisterWatch();
      }
    });
  };
});
