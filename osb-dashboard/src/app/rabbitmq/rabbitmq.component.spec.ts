import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { RabbitMqComponent } from './rabbitmq.component';

describe('MongodbComponent', () => {
  let component: RabbitMqComponent;
  let fixture: ComponentFixture<RabbitMqComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ RabbitMqComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(RabbitMqComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });
});
