/**
 * Utilidades de interfaz del frontend (Cliente).
 * - Confirmación de acciones destructivas ([data-confirm] en enlaces, formularios y botones).
 * - Filtro en vivo de tablas y listas ([data-filter-input="#idTabla"]).
 * - Filtro por botones ([data-filter-btn] con data-filter-target y data-filter-value).
 * - Filtro por rango de fechas ([data-date-filter="#idTabla"] con [data-date-from]/[data-date-to])
 *   y ordenamiento por fecha ([data-sort-target="#idTabla"]: recientes/antiguos).
 * - Limpieza de los filtros de una tabla ([data-filter-reset="#idTabla"]).
 * - Exportación CSV de tablas ([data-export-csv="#idTabla"]; las celdas con [data-export-skip] se omiten).
 * - Impresión de la página ([data-print]).
 * - Cierre automático de mensajes flash y estado de carga en formularios.
 * - Campos obligatorios condicionales
 *   ([data-required-when="#selector" data-required-value="VALOR"]).
 * - Fecha mínima de un campo según otro ([data-min-from="#selector"]).
 */
(function () {
  'use strict';

  // ------------------------------------------------------------------
  // 1. Modal de confirmación reutilizable
  // ------------------------------------------------------------------
  let confirmModal = null;
  let confirmResolve = null;

  function ensureConfirmModal() {
    if (confirmModal) {
      return confirmModal;
    }
    const wrapper = document.createElement('div');
    wrapper.innerHTML =
      '<div class="modal fade" id="appConfirmModal" tabindex="-1" aria-hidden="true">' +
      '  <div class="modal-dialog modal-dialog-centered">' +
      '    <div class="modal-content">' +
      '      <div class="modal-header">' +
      '        <h5 class="modal-title"><i class="bi bi-question-circle me-2"></i>Confirmar acción</h5>' +
      '        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Cerrar"></button>' +
      '      </div>' +
      '      <div class="modal-body"><p class="mb-0" id="appConfirmMessage"></p></div>' +
      '      <div class="modal-footer">' +
      '        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancelar</button>' +
      '        <button type="button" class="btn btn-danger" id="appConfirmAccept">Confirmar</button>' +
      '      </div>' +
      '    </div>' +
      '  </div>' +
      '</div>';
    document.body.appendChild(wrapper.firstChild);

    const modalEl = document.getElementById('appConfirmModal');
    confirmModal = new bootstrap.Modal(modalEl);
    modalEl.addEventListener('hidden.bs.modal', function () {
      if (confirmResolve) {
        confirmResolve(false);
        confirmResolve = null;
      }
    });
    document.getElementById('appConfirmAccept').addEventListener('click', function () {
      confirmModal.hide();
      if (confirmResolve) {
        confirmResolve(true);
        confirmResolve = null;
      }
    });
    return confirmModal;
  }

  function askConfirm(message) {
    return new Promise(function (resolve) {
      confirmResolve = resolve;
      ensureConfirmModal();
      document.getElementById('appConfirmMessage').textContent = message;
      confirmModal.show();
    });
  }

  document.addEventListener('click', function (event) {
    const trigger = event.target.closest('a[data-confirm]');
    if (!trigger || trigger.dataset.confirmed === 'true') {
      return;
    }
    event.preventDefault();
    askConfirm(trigger.dataset.confirm || '¿Confirmás esta acción?').then(function (ok) {
      if (ok) {
        trigger.dataset.confirmed = 'true';
        trigger.click();
      }
    });
  });

  // Botones de formulario con confirmación (el valor del botón se envía al confirmar)
  document.addEventListener('click', function (event) {
    const button = event.target.closest('button[data-confirm]');
    if (!button || button.dataset.confirmed === 'true') {
      return;
    }
    event.preventDefault();
    askConfirm(button.dataset.confirm || '¿Confirmás esta acción?').then(function (ok) {
      if (ok) {
        button.dataset.confirmed = 'true';
        button.click();
      }
    });
  });

  document.addEventListener('submit', function (event) {
    const form = event.target.closest('form[data-confirm]');
    if (!form || form.dataset.confirmed === 'true') {
      return;
    }
    event.preventDefault();
    askConfirm(form.dataset.confirm || '¿Confirmás esta acción?').then(function (ok) {
      if (ok) {
        form.dataset.confirmed = 'true';
        form.submit();
      }
    });
  });

  // ------------------------------------------------------------------
  // 2. Filtro en vivo de tablas y listas
  // ------------------------------------------------------------------
  function filterTarget(targetSelector) {
    return document.querySelector(targetSelector);
  }

  // Estado de filtros por tabla: texto, rango de fechas y filtro por botones se
  // combinan entre sí (antes cada criterio pisaba el resultado del anterior).
  function filterState(target) {
    if (!target.filterState) {
      target.filterState = { term: '', from: '', to: '', tagAttr: 'data-filter-tags', tagValue: '' };
    }
    return target.filterState;
  }

  function applyFilters(target) {
    const state = filterState(target);
    const rows = target.querySelectorAll('[data-filter-item], tbody tr');
    let visible = 0;
    rows.forEach(function (row) {
      const text = row.textContent.toLowerCase();
      const date = row.getAttribute('data-filter-date') || '';
      const tags = (row.getAttribute(state.tagAttr) || '').toLowerCase();
      const match = (state.term === '' || text.indexOf(state.term) !== -1)
        && (state.from === '' || (date !== '' && date >= state.from))
        && (state.to === '' || (date !== '' && date <= state.to))
        && (state.tagValue === '' || tags.indexOf(state.tagValue) !== -1);
      row.classList.toggle('d-none', !match);
      if (match) {
        visible += 1;
      }
    });
    const empty = document.querySelector('[data-filter-empty="' + target.id + '"]');
    if (empty) {
      empty.classList.toggle('d-none', visible !== 0);
    }
  }

  function filterRows(target, term) {
    filterState(target).term = term.trim().toLowerCase();
    applyFilters(target);
  }

  document.querySelectorAll('[data-filter-input]').forEach(function (input) {
    const target = filterTarget(input.dataset.filterInput);
    if (!target) {
      return;
    }
    input.addEventListener('input', function () {
      filterRows(target, input.value);
    });
  });

  document.querySelectorAll('[data-filter-btn]').forEach(function (button) {
    button.addEventListener('click', function () {
      const group = button.dataset.filterGroup || '';
      if (group) {
        document.querySelectorAll('[data-filter-btn][data-filter-group="' + group + '"]').forEach(function (other) {
          other.classList.remove('active');
        });
        button.classList.add('active');
      }
      const target = filterTarget(button.dataset.filterTarget);
      if (!target) {
        return;
      }
      const state = filterState(target);
      state.tagAttr = button.dataset.filterAttr || 'data-filter-tags';
      state.tagValue = (button.dataset.filterValue || '').toLowerCase();
      applyFilters(target);
    });
  });

  // Rango de fechas: [data-date-filter="#idTabla"] agrupa los dos campos.
  document.querySelectorAll('[data-date-filter]').forEach(function (group) {
    const target = filterTarget(group.dataset.dateFilter);
    if (!target) {
      return;
    }
    const from = group.querySelector('[data-date-from]');
    const to = group.querySelector('[data-date-to]');
    const sync = function () {
      const state = filterState(target);
      state.from = from ? from.value : '';
      state.to = to ? to.value : '';
      applyFilters(target);
    };
    [from, to].forEach(function (field) {
      if (field) {
        field.addEventListener('input', sync);
        field.addEventListener('change', sync);
      }
    });
  });

  // Limpiar: restablece texto, fechas y filtro por botones de la tabla indicada.
  document.querySelectorAll('[data-filter-reset]').forEach(function (button) {
    button.addEventListener('click', function () {
      const target = filterTarget(button.dataset.filterReset);
      if (!target) {
        return;
      }
      const scope = button.closest('[data-filter-scope]') || button.closest('.list-toolbar') || document;
      scope.querySelectorAll('[data-filter-input], [data-date-from], [data-date-to]').forEach(function (field) {
        field.value = '';
      });
      const state = filterState(target);
      state.term = '';
      state.from = '';
      state.to = '';
      state.tagValue = '';
      document.querySelectorAll('[data-filter-btn]').forEach(function (other) {
        if (other.dataset.filterTarget === button.dataset.filterReset) {
          other.classList.toggle('active', (other.dataset.filterValue || '') === '');
        }
      });
      applyFilters(target);
    });
  });

  // Ordenamiento por fecha: [data-sort-target="#idTabla"] con recientes/antiguos.
  document.querySelectorAll('[data-sort-target]').forEach(function (select) {
    const target = filterTarget(select.dataset.sortTarget);
    if (!target) {
      return;
    }
    const sort = function () {
      const rows = Array.prototype.slice.call(target.querySelectorAll('[data-filter-item]'));
      if (rows.length === 0) {
        return;
      }
      // Las filas se mueven dentro de su contenedor (tbody de la tabla, lista, etc.).
      const body = rows[0].parentElement || target;
      rows.sort(function (a, b) {
        const dateA = a.getAttribute('data-sort-date') || '';
        const dateB = b.getAttribute('data-sort-date') || '';
        if (dateA !== dateB) {
          return dateA < dateB ? -1 : 1;
        }
        return Number(a.getAttribute('data-sort-id') || 0) - Number(b.getAttribute('data-sort-id') || 0);
      });
      if (select.value !== 'antiguos') {
        rows.reverse();
      }
      rows.forEach(function (row) {
        body.appendChild(row);
      });
    };
    select.addEventListener('change', sort);
    sort();
  });

  // ------------------------------------------------------------------
  // 3. Exportación CSV de tablas
  // ------------------------------------------------------------------
  function tableToCsv(table) {
    const lines = [];
    table.querySelectorAll('tr').forEach(function (row) {
      if (row.classList.contains('d-none')) {
        return;
      }
      const cells = [];
      row.querySelectorAll('th, td').forEach(function (cell) {
        if (cell.hasAttribute('data-export-skip')) {
          return;
        }
        let text = cell.innerText.replace(/\s+/g, ' ').trim();
        text = text.replace(/"/g, '""');
        cells.push('"' + text + '"');
      });
      if (cells.length > 0) {
        lines.push(cells.join(';'));
      }
    });
    return lines.join('\r\n');
  }

  document.querySelectorAll('[data-export-csv]').forEach(function (button) {
    button.addEventListener('click', function () {
      const table = document.querySelector(button.dataset.exportCsv);
      if (!table) {
        return;
      }
      const csv = '\ufeff' + tableToCsv(table);
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
      const link = document.createElement('a');
      link.href = URL.createObjectURL(blob);
      link.download = (button.dataset.exportName || 'reporte') + '.csv';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(link.href);
    });
  });

  // ------------------------------------------------------------------
  // 4. Impresión
  // ------------------------------------------------------------------
  document.querySelectorAll('[data-print]').forEach(function (button) {
    button.addEventListener('click', function () {
      window.print();
    });
  });

  // ------------------------------------------------------------------
  // 5. Mensajes flash: cierre automático de éxito/información
  // ------------------------------------------------------------------
  document.querySelectorAll('.app-flash .alert-success, .app-flash .alert-info').forEach(function (alertEl) {
    window.setTimeout(function () {
      if (window.bootstrap && bootstrap.Alert) {
        bootstrap.Alert.getOrCreateInstance(alertEl).close();
      }
    }, 6000);
  });

  // ------------------------------------------------------------------
  // 6. Estado de carga en formularios (data-loading)
  // ------------------------------------------------------------------
  document.querySelectorAll('form[data-loading]').forEach(function (form) {
    form.addEventListener('submit', function () {
      const button = form.querySelector('button[type="submit"]');
      if (button && !button.disabled) {
        button.dataset.originalHtml = button.innerHTML;
        button.disabled = true;
        button.innerHTML = '<span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>Procesando…';
      }
    });
  });

  // ------------------------------------------------------------------
  // 7. Campos obligatorios condicionales (data-required-when)
  // ------------------------------------------------------------------
  // El campo con [data-required-when] pasa a ser obligatorio según el valor
  // del control indicado. Sin data-required-value alcanza con que tenga un valor.
  document.querySelectorAll('[data-required-when]').forEach(function (field) {
    const source = document.querySelector(field.dataset.requiredWhen);
    if (!source) {
      return;
    }
    const expected = field.dataset.requiredValue;
    const sync = function () {
      field.required = expected === undefined ? source.value !== '' : source.value === expected;
    };
    source.addEventListener('change', sync);
    source.addEventListener('input', sync);
    sync();
  });

  // ------------------------------------------------------------------
  // 8. Fecha mínima de un campo según otro (data-min-from)
  // ------------------------------------------------------------------
  // El campo con [data-min-from="#selector"] no admite fechas anteriores al
  // valor del control indicado: se usa para que la fecha de fin no quede antes
  // de la de inicio. La regla definitiva la sigue validando el Servidor.
  document.querySelectorAll('[data-min-from]').forEach(function (field) {
    const source = document.querySelector(field.dataset.minFrom);
    if (!source) {
      return;
    }
    const sync = function () {
      if (source.value) {
        field.min = source.value;
      } else {
        field.removeAttribute('min');
      }
    };
    source.addEventListener('change', sync);
    source.addEventListener('input', sync);
    sync();
  });
})();
