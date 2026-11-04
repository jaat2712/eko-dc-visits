import { test } from 'node:test';
import assert from 'node:assert/strict';
import { formatDistance, geofenceChip, syncChip, plannedLabel } from './chips.ts';

test('geofenceChip INSIDE is green "Near CSP" with the distance', () => {
  const chip = geofenceChip('INSIDE', null, 35.4);
  assert.equal(chip.label, 'Near CSP · 35 m');
  assert.ok(chip.className.includes('chip-green'));
  assert.equal(chip.title, '35 m from CSP pin at check-in time');
});

test('geofenceChip OUTSIDE_FLAGGED is amber "Away from CSP" and carries out_of_radius_reason in the tooltip', () => {
  const chip = geofenceChip('OUTSIDE_FLAGGED', 'MASTER_PIN_WRONG', 9606);
  assert.equal(chip.label, 'Away from CSP · 9.6 km');
  assert.ok(chip.className.includes('chip-amber'));
  assert.equal(chip.title, '9.6 km from CSP pin at check-in time. Reason: MASTER_PIN_WRONG');
});

test('geofenceChip tolerates null/absent reason and distance (spec allows both)', () => {
  assert.equal(geofenceChip('OUTSIDE_FLAGGED', null).label, 'Away from CSP');
  assert.equal(geofenceChip('OUTSIDE_FLAGGED').title, 'At check-in time');
  assert.equal(geofenceChip('INSIDE').label, 'Near CSP');
});

test('formatDistance switches to km at 1000 m', () => {
  assert.equal(formatDistance(211.4), '211 m');
  assert.equal(formatDistance(999), '999 m');
  assert.equal(formatDistance(1000), '1.0 km');
  assert.equal(formatDistance(1520), '1.5 km');
});

test('syncChip LATE_SYNC is amber; SYNCED is neutral', () => {
  assert.ok(syncChip('LATE_SYNC').className.includes('chip-amber'));
  assert.equal(syncChip('LATE_SYNC').label, 'LATE_SYNC');
  assert.ok(syncChip('SYNCED').className.includes('chip-neutral'));
});

test('plannedLabel maps true/false/absent', () => {
  assert.equal(plannedLabel(true), 'Planned');
  assert.equal(plannedLabel(false), 'Unplanned');
  assert.equal(plannedLabel(undefined), '—');
});

import { attendanceChip } from './chips.ts';

test('attendanceChip: ON_DUTY green, ENDED neutral, NOT_STARTED amber with tooltip', () => {
  assert.equal(attendanceChip('ON_DUTY').className, 'chip chip-green');
  assert.equal(attendanceChip('ENDED').className, 'chip chip-neutral');
  const ns = attendanceChip('NOT_STARTED');
  assert.equal(ns.className, 'chip chip-amber');
  assert.ok(ns.title.length > 0, 'NOT_STARTED explains itself on hover');
});
