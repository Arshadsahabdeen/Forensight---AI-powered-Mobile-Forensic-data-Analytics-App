export interface MockUser {
  id: number;
  username: string;
  fullName: string;
  role: 'ADMIN' | 'INVESTIGATOR' | 'REVIEWER';
}

export const mockUsers: MockUser[] = [
  { id: 1, username: 'aarav.sharma', fullName: 'Aarav Sharma', role: 'INVESTIGATOR' },
  { id: 2, username: 'maya.khan', fullName: 'Maya Khan', role: 'INVESTIGATOR' },
  { id: 3, username: 'jyoti.rao', fullName: 'Jyoti Rao', role: 'INVESTIGATOR' },
  { id: 4, username: 'nikhil.patel', fullName: 'Nikhil Patel', role: 'INVESTIGATOR' },
  { id: 5, username: 'aditi.mehra', fullName: 'Aditi Mehra', role: 'INVESTIGATOR' },
  { id: 6, username: 'rahul.menon', fullName: 'Rahul Menon', role: 'INVESTIGATOR' },
  { id: 7, username: 'priya.singh', fullName: 'Priya Singh', role: 'INVESTIGATOR' },
  { id: 8, username: 'karan.desai', fullName: 'Karan Desai', role: 'INVESTIGATOR' },
  { id: 9, username: 'neha.verma', fullName: 'Neha Verma', role: 'INVESTIGATOR' },
  { id: 10, username: 'siddharth.iyer', fullName: 'Siddharth Iyer', role: 'INVESTIGATOR' }
];
